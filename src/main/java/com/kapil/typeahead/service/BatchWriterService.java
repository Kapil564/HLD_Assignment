package com.kapil.typeahead.service;

import com.kapil.typeahead.cache.ConsistentHashingService;
import com.kapil.typeahead.entity.SearchQuery;
import com.kapil.typeahead.repository.SearchQueryRepository;
import com.kapil.typeahead.store.SearchStore;
import com.kapil.typeahead.trie.Trie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchWriterService {

    private final Trie trie;
    private final SearchStore searchStore;
    private final ConsistentHashingService consistentHashingService;
    private final SearchQueryRepository searchQueryRepository;
    private final MetricsService metricsService;

    // Lock object to synchronize addToBatch and flush
    private final Object bufferLock = new Object();

    private ConcurrentHashMap<String, Long> batchBuffer = new ConcurrentHashMap<>();
    private int bufferSize = 0;

    private static final int FLUSH_THRESHOLD = 1000;
    private static final String CACHE_PREFIX = "suggest:";

    public void addToBatch(String query, long delta) {
        metricsService.recordSearchRequest(delta);

        boolean shouldFlush = false;
        synchronized (bufferLock) {
            batchBuffer.merge(query, delta, Long::sum);
            bufferSize++;
            if (bufferSize >= FLUSH_THRESHOLD) {
                shouldFlush = true;
            }
        }

        if (shouldFlush) {
            flush();
        }
    }

    @Scheduled(fixedRate = 5000)
    public void scheduledFlush() {
        synchronized (bufferLock) {
            if (batchBuffer.isEmpty()) {
                return;
            }
        }
        flush();
    }

    @Transactional
    public void flush() {
        ConcurrentHashMap<String, Long> snapshot;
        synchronized (bufferLock) {
            if (batchBuffer.isEmpty()) {
                return;
            }
            snapshot = batchBuffer;
            batchBuffer = new ConcurrentHashMap<>();
            bufferSize = 0;
        }

        log.info("Flushing batch buffer with {} entries to database", snapshot.size());
        metricsService.recordDbWrites(snapshot.size());

        // Update in-memory counts
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            String query = entry.getKey();
            Long delta = entry.getValue();
            searchStore.increment(query, delta);
            searchStore.incrementRecent(query, delta);

            // Dynamic Trie insertion
            trie.insert(query);
        }

        // Batch fetch existing entries
        List<SearchQuery> existingQueries = searchQueryRepository.findByQueryTextIn(snapshot.keySet());
        Map<String, SearchQuery> queryMap = existingQueries.stream()
                .collect(Collectors.toMap(SearchQuery::getQueryText, q -> q));

        // Prepare list for batch saving
        List<SearchQuery> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            String query = entry.getKey();
            Long delta = entry.getValue();

            SearchQuery searchQuery = queryMap.get(query);
            if (searchQuery != null) {
                searchQuery.setCount(searchQuery.getCount() + delta);
                searchQuery.setLastUpdated(now);
                searchQuery.setLastSearchedAt(now);
            } else {
                searchQuery = new SearchQuery(null, query, delta, now, now);
            }
            toSave.add(searchQuery);
        }

        // Batch save to DB
        searchQueryRepository.saveAll(toSave);

        // Invalidate caches
        invalidateCacheForQueries(snapshot.keySet());
    }

    private void invalidateCacheForQueries(Set<String> queries) {
        Set<String> keysToDelete = queries.stream()
                .flatMap(query -> generatePrefixKeys(query).stream())
                .collect(Collectors.toSet());

        if (!keysToDelete.isEmpty()) {
            Map<StringRedisTemplate, Set<String>> keysByTemplate = new HashMap<>();
            for (String key : keysToDelete) {
                StringRedisTemplate template = consistentHashingService.getTemplate(key);
                if (template != null) {
                    keysByTemplate.computeIfAbsent(template, t -> new HashSet<>()).add(key);
                }
            }

            for (Map.Entry<StringRedisTemplate, Set<String>> entry : keysByTemplate.entrySet()) {
                entry.getKey().delete(entry.getValue());
                log.info("Invalidated {} cache keys on a consistent hashing node", entry.getValue().size());
            }
        }
    }

    private Set<String> generatePrefixKeys(String query) {

        return IntStream.rangeClosed(1, query.length())
                .mapToObj(i -> CACHE_PREFIX + query.substring(0, i))
                .collect(Collectors.toSet());
    }

    public int getBufferSize() {
        synchronized (bufferLock) {
            return bufferSize;
        }
    }
}
