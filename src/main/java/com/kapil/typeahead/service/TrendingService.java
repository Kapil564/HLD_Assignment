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

        return searchStore.getCounts().keySet().stream()
                .map(query -> new TrendingItem(query, searchStore.getTrendingScore(query), searchStore.getCount(query)))
                .sorted(Comparator.comparingDouble(TrendingItem::getScore).reversed()
                        .thenComparing(Comparator.comparingLong(TrendingItem::getCount).reversed())
                        .thenComparing(TrendingItem::getQuery))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static class TrendingItem {

        private String query;
        private double score;
        private long count;

        public TrendingItem(String query, double score, long count) {
            this.query = query;
            this.score = score;
            this.count = count;
        }

        public String getQuery() {
            return query;
        }

        public double getScore() {
            return score;
        }

        public long getCount() {
            return count;
        }
    }
}
