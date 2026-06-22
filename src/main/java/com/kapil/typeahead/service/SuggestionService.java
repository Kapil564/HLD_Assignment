package com.kapil.typeahead.service;

import com.kapil.typeahead.cache.ConsistentHashingService;
import com.kapil.typeahead.cache.RedisNode;
import com.kapil.typeahead.dto.SuggestResponse;
import com.kapil.typeahead.store.SearchStore;
import com.kapil.typeahead.trie.Trie;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final Trie trie;
    private final SearchStore searchStore;
    private final ConsistentHashingService consistentHashingService;
    private final MetricsService metricsService;

    private static final String CACHE_PREFIX = "suggest:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public SuggestResponse suggest(String prefix) {
        long startTime = System.nanoTime();
        boolean isHit = false;

        String cacheKey = CACHE_PREFIX + prefix.toLowerCase();

        StringRedisTemplate template = consistentHashingService.getTemplate(cacheKey);
        String cachedResult = template != null ? template.opsForValue().get(cacheKey) : null;

        List<String> sortedQueries;

        if (cachedResult != null) {
            isHit = true;
            RedisNode node = consistentHashingService.getNode(cacheKey);
            System.out.println("Cache HIT for: " + prefix + " on node " + (node != null ? node.getName() : "unknown"));
            sortedQueries = cachedResult.isEmpty() ? List.of() : List.of(cachedResult.split(","));
        } else {
            System.out.println("Cache MISS for: " + prefix);

            List<String> matches = trie.search(prefix.toLowerCase(), 50);

            System.out.println("Matches found = " + matches.size());

            sortedQueries = matches.stream()
                    .sorted(Comparator.comparingDouble(searchStore::getTrendingScore).reversed())
                    .limit(10)
                    .toList();

            if (template != null) {
                template.opsForValue().set(
                        cacheKey,
                        String.join(",", sortedQueries),
                        CACHE_TTL
                );
            }
        }

        List<SuggestResponse.SuggestionItem> suggestions = sortedQueries.stream()
                .map(q -> new SuggestResponse.SuggestionItem(q, searchStore.getCount(q)))
                .toList();

        long duration = System.nanoTime() - startTime;
        metricsService.recordSuggestRequest(isHit, duration);

        return new SuggestResponse(prefix, suggestions);
    }
}