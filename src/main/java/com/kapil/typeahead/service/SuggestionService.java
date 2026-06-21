package com.kapil.typeahead.service;

import com.kapil.typeahead.store.SearchStore;
import com.kapil.typeahead.trie.Trie;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final Trie trie;
    private final SearchStore searchStore;
    private final StringRedisTemplate redisTemplate;

    private static final String CACHE_PREFIX = "suggest:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public List<String> suggest(String prefix) {

        String cacheKey = CACHE_PREFIX + prefix.toLowerCase();

        String cachedResult = redisTemplate.opsForValue().get(cacheKey);

        if (cachedResult != null) {
            System.out.println("Cache HIT for: " + prefix);
            return List.of(cachedResult.split(","));
        }

        System.out.println("Cache MISS for: " + prefix);

        List<String> matches =
                trie.search(prefix.toLowerCase(), 50);

        System.out.println("Matches found = " + matches.size());

        List<String> sortedMatches = matches.stream()
                .sorted(
                        Comparator.comparingLong(searchStore::getCount)
                                .reversed()
                )
                .limit(10)
                .toList();

        redisTemplate.opsForValue().set(
                cacheKey,
                String.join(",", sortedMatches),
                CACHE_TTL
        );

        return sortedMatches;
    }
}