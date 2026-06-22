package com.kapil.typeahead.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private final AtomicLong suggestRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final ConcurrentLinkedQueue<Long> suggestLatencies = new ConcurrentLinkedQueue<>();

    private final AtomicLong searchRequests = new AtomicLong(0);
    private final AtomicLong dbWrites = new AtomicLong(0);

    private static final int MAX_LATENCIES_SAMPLES = 10000;

    public void recordSuggestRequest(boolean cacheHit, long latencyNs) {
        suggestRequests.incrementAndGet();
        if (cacheHit) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }

        long latencyMs = latencyNs / 1_000_000; // convert to ms
        suggestLatencies.add(latencyMs);
        if (suggestLatencies.size() > MAX_LATENCIES_SAMPLES) {
            suggestLatencies.poll();
        }
    }

    public void recordSearchRequest(long count) {
        searchRequests.addAndGet(count);
    }

    public void recordDbWrites(long count) {
        dbWrites.addAndGet(count);
    }

    public Map<String, Object> getMetricsReport() {
        Map<String, Object> report = new LinkedHashMap<>();

        long totalSuggests = suggestRequests.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = totalSuggests > 0 ? (double) hits / totalSuggests * 100.0 : 0.0;

        List<Long> sortedLatencies = new ArrayList<>(suggestLatencies);
        Collections.sort(sortedLatencies);

        double avgLatency = 0.0;
        long p95Latency = 0;
        if (!sortedLatencies.isEmpty()) {
            avgLatency = sortedLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
            int p95Index = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
            p95Latency = sortedLatencies.get(Math.max(0, p95Index));
        }

        long searches = searchRequests.get();
        long writes = dbWrites.get();
        double writeReduction = searches > 0 ? (double) (searches - writes) / searches * 100.0 : 0.0;

        report.put("totalSuggestRequests", totalSuggests);
        report.put("cacheHits", hits);
        report.put("cacheMisses", misses);
        report.put("cacheHitRatePercent", String.format("%.2f%%", hitRate));
        report.put("averageSuggestLatencyMs", String.format("%.2f ms", avgLatency));
        report.put("p95SuggestLatencyMs", p95Latency + " ms");
        report.put("totalSearchRequestsSubmitted", searches);
        report.put("actualDbWritesFlushed", writes);
        report.put("writeReductionPercent", String.format("%.2f%%", writeReduction));

        return report;
    }
}
