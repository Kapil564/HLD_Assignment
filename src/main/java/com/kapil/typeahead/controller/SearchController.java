package com.kapil.typeahead.controller;

import com.kapil.typeahead.dto.SearchResponse;
import com.kapil.typeahead.service.SearchService;
import com.kapil.typeahead.service.SuggestionService;
import com.kapil.typeahead.service.TrendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {

    private final SuggestionService suggestionService;
    private final SearchService searchService;
    private final StringRedisTemplate redisTemplate;
    private final TrendingService trendingService;

    @GetMapping("/suggest")
    public List<String> suggest(@RequestParam String q) {

        System.out.println("Request received: " + q);

        return suggestionService.suggest(q);
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestBody SearchRequest request) {

        System.out.println("Search submitted: " + request.getQuery());

        searchService.submitSearch(request.getQuery());

        return new SearchResponse("Searched");
    }

    @GetMapping("/cache/debug")
    public CacheDebugResponse cacheDebug(@RequestParam String prefix) {

        String cacheKey = "suggest:" + prefix.toLowerCase();
        String cachedResult = redisTemplate.opsForValue().get(cacheKey);

        String status = cachedResult != null ? "HIT" : "MISS";
        String cacheNode = "Node-1";

        return new CacheDebugResponse(prefix, cacheNode, status);
    }

    @GetMapping("/trending")
    public List<TrendingService.TrendingItem> trending() {

        return trendingService.getTrending(10);
    }

    public static class SearchRequest {

        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }

    public static class CacheDebugResponse {

        private String prefix;
        private String cacheNode;
        private String status;

        public CacheDebugResponse(String prefix, String cacheNode, String status) {
            this.prefix = prefix;
            this.cacheNode = cacheNode;
            this.status = status;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getCacheNode() {
            return cacheNode;
        }

        public String getStatus() {
            return status;
        }
    }
}