package com.kapil.typeahead.controller;

import com.kapil.typeahead.cache.ConsistentHashingService;
import com.kapil.typeahead.cache.RedisNode;
import com.kapil.typeahead.dto.SearchResponse;
import com.kapil.typeahead.dto.SuggestResponse;
import com.kapil.typeahead.service.MetricsService;
import com.kapil.typeahead.service.SearchService;
import com.kapil.typeahead.service.SuggestionService;
import com.kapil.typeahead.service.TrendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {

    private final SuggestionService suggestionService;
    private final SearchService searchService;
    private final ConsistentHashingService consistentHashingService;
    private final TrendingService trendingService;
    private final MetricsService metricsService;

    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        return metricsService.getMetricsReport();
    }

    @GetMapping("/suggest")
    public SuggestResponse suggest(@RequestParam String q) {

        System.out.println("Request received: " + q);

        return suggestionService.suggest(q);
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestBody SearchRequest request) {

        System.out.println("Search submitted: " + request.getQuery());

        searchService.submitSearch(request.getQuery());

        return new SearchResponse("Success", request.getQuery());
    }

    @GetMapping("/cache/debug")
    public CacheDebugResponse cacheDebug(@RequestParam String prefix) {

        String cacheKey = "suggest:" + prefix.toLowerCase();
        
        RedisNode node = consistentHashingService.getNode(cacheKey);
        StringRedisTemplate template = consistentHashingService.getTemplate(cacheKey);
        String cachedResult = template != null ? template.opsForValue().get(cacheKey) : null;

        String status = cachedResult != null ? "HIT" : "MISS";
        String cacheNode = node != null ? node.getName() : "None";

        return new CacheDebugResponse(prefix, cacheNode, status);
    }

    @GetMapping("/trending")
    public Map<String, List<TrendingService.TrendingItem>> trending() {

        return Map.of("trending", trendingService.getTrending(10));
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