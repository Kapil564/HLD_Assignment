package com.kapil.typeahead.service;

import com.kapil.typeahead.cache.ConsistentHashingService;
import com.kapil.typeahead.entity.SearchQuery;
import com.kapil.typeahead.repository.SearchQueryRepository;
import com.kapil.typeahead.store.SearchStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchWriterService {

    private final SearchStore searchStore;
    private final ConsistentHashingService consistentHashingService;
    private final SearchQueryRepository searchQueryRepository;
    private final MetricsService metricsService;

    private final Map<String, Long> batchBuffer = new ConcurrentHashMap<>();
    private final AtomicInteger bufferSize = new AtomicInteger(0);

    private static final int FLUSH_THRESHOLD = 1000;
    private static final String CACHE_PREFIX = "suggest:";

    public void addToBatch(String query, long delta) {
        metricsService.recordSearchRequest(delta);

        batchBuffer.merge(query, delta, Long::sum);
        int size = bufferSize.incrementAndGet();

        if (size >= FLUSH_THRESHOLD) {
            flush();
        }
    }

    @Scheduled(fixedRate = 5000)
    public void scheduledFlush() {

        if (!batchBuffer.isEmpty()) {
            flush();
        }
    }

    @Transactional
    public void flush() {

        if (batchBuffer.isEmpty()) {
            return;
        }

        log.info("Flushing batch buffer with {} entries", batchBuffer.size());
        metricsService.recordDbWrites(batchBuffer.size());

        Set<String> queriesToInvalidate = batchBuffer.keySet();

        for (Map.Entry<String, Long> entry : batchBuffer.entrySet()) {

            String query = entry.getKey();
            Long delta = entry.getValue();

            searchStore.increment(query, delta);
            searchStore.incrementRecent(query, delta);

            SearchQuery searchQuery = searchQueryRepository.findByQueryText(query)
                    .orElse(new SearchQuery(null, query, 0L, LocalDateTime.now(), LocalDateTime.now()));

            searchQuery.setCount(searchQuery.getCount() + delta);
            searchQuery.setLastUpdated(LocalDateTime.now());
            searchQuery.setLastSearchedAt(LocalDateTime.now());

            searchQueryRepository.save(searchQuery);
        }

        invalidateCacheForQueries(queriesToInvalidate);

        batchBuffer.clear();
        bufferSize.set(0);

        log.info("Batch flush completed");
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
        return bufferSize.get();
    }
}
