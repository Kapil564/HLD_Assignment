package com.kapil.typeahead.store;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SearchStore {

    private final Map<String, Long> counts = new ConcurrentHashMap<>();

    public Map<String, Long> getCounts() {
        return counts;
    }

    public void put(String query, long count) {
        counts.put(query, count);
    }

    public long getCount(String query) {
        return counts.getOrDefault(query, 0L);
    }

    public void increment(String query, long delta) {
        counts.merge(query, delta, Long::sum);
    }

    public boolean contains(String query) {
        return counts.containsKey(query);
    }

    public int size() {
        return counts.size();
    }
}