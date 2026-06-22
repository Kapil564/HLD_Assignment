package com.kapil.typeahead.store;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SearchStore {

    private final Map<String, Long> counts = new ConcurrentHashMap<>();
    private final Map<String, Double> recentCounts = new ConcurrentHashMap<>();

    public Map<String, Long> getCounts() {
        return counts;
    }

    public void put(String query, long count) {
        counts.put(query, count);
    }

    public long getCount(String query) {
        return counts.getOrDefault(query, 0L);
    }

    public double getRecentCount(String query) {
        return recentCounts.getOrDefault(query, 0.0);
    }

    public double getTrendingScore(String query) {
        double recent = getRecentCount(query);
        long total = getCount(query);
        return 0.7 * recent + 0.3 * total;
    }

    public void increment(String query, long delta) {
        counts.merge(query, delta, Long::sum);
    }

    public void incrementRecent(String query, double delta) {
        recentCounts.merge(query, delta, Double::sum);
    }

    @Scheduled(fixedRate = 10000)
    public void decayRecentCounts() {
        if (!recentCounts.isEmpty()) {
            recentCounts.replaceAll((query, val) -> val * 0.9);
            // clean up tiny values to prevent memory footprint growth
            recentCounts.entrySet().removeIf(entry -> entry.getValue() < 0.01);
        }
    }

    public boolean contains(String query) {
        return counts.containsKey(query);
    }

    public int size() {
        return counts.size();
    }
}