package com.kapil.typeahead.service;

import com.kapil.typeahead.store.SearchStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrendingService {

    private final SearchStore searchStore;

    public List<TrendingItem> getTrending(int limit) {

        return searchStore.getCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new TrendingItem(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    public static class TrendingItem {

        private String query;
        private long count;

        public TrendingItem(String query, long count) {
            this.query = query;
            this.count = count;
        }

        public String getQuery() {
            return query;
        }

        public long getCount() {
            return count;
        }
    }
}
