package com.kapil.typeahead.service;

import com.kapil.typeahead.store.SearchStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchWriterService {

    private final SearchStore searchStore;
    private final StringRedisTemplate redisTemplate;

    private final Map<String, Long> batchBuffer = new ConcurrentHashMap<>();
    private final AtomicInteger bufferSize = new AtomicInteger(0);

    private static final int FLUSH_THRESHOLD = 1000;
    private static final String CACHE_PREFIX = "suggest:";

    public void addToBatch(String query, long delta) {

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

    private void flush() {

        if (batchBuffer.isEmpty()) {
            return;
        }

        log.info("Flushing batch buffer with {} entries", batchBuffer.size());

        Set<String> queriesToInvalidate = batchBuffer.keySet();

        for (Map.Entry<String, Long> entry : batchBuffer.entrySet()) {

            searchStore.increment(entry.getKey(), entry.getValue());
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
            redisTemplate.delete(keysToDelete);
            log.info("Invalidated {} cache keys", keysToDelete.size());
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
