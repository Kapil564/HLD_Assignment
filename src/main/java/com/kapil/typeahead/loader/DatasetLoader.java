package com.kapil.typeahead.loader;

import com.kapil.typeahead.entity.SearchQuery;
import com.kapil.typeahead.repository.SearchQueryRepository;
import com.kapil.typeahead.store.SearchStore;
import com.kapil.typeahead.trie.Trie;
import com.opencsv.CSVReader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class DatasetLoader {

    private final Trie trie;
    private final StringRedisTemplate redisTemplate;
    private final SearchStore searchStore;
    private final SearchQueryRepository searchQueryRepository;

    private static final int BATCH_SIZE = 1000;

    @PostConstruct
    @Transactional
    public void load() {

        if (searchQueryRepository.count() > 0) {
            System.out.println("Database already populated. Warming up in-memory Trie and SearchStore from database...");
            List<Object[]> results = searchQueryRepository.findAllQueriesAndCounts();
            int count = 0;
            for (Object[] row : results) {
                String queryText = (String) row[0];
                Long totalCount = (Long) row[1];
                if (queryText != null) {
                    String normQuery = queryText.toLowerCase();
                    trie.insert(normQuery);
                    searchStore.put(normQuery, totalCount != null ? totalCount : 0L);
                    count++;
                }
            }
            redisTemplate.opsForValue().set("dataset:loaded", "true");
            System.out.println("Warmup completed. Loaded " + count + " queries from database into memory.");
            return;
        }

        System.out.println("Database is empty. Loading dataset from CSV...");

        try (CSVReader reader = new CSVReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResourceAsStream("dataset.csv"))
        ))) {

            String[] header = reader.readNext();

            String[] row;
            int count = 0;
            List<SearchQuery> batch = new ArrayList<>(BATCH_SIZE);

            while ((row = reader.readNext()) != null) {

                String query = row[0];
                long totalCount = Long.parseLong(row[1]);

                trie.insert(query.toLowerCase());
                searchStore.put(query.toLowerCase(), totalCount);

                SearchQuery searchQuery = searchQueryRepository.findByQueryText(query.toLowerCase())
                        .orElse(new SearchQuery(
                                null,
                                query.toLowerCase(),
                                totalCount,
                                LocalDateTime.now(),
                                LocalDateTime.now()
                        ));

                if (searchQuery.getId() == null) {
                    batch.add(searchQuery);
                } else {
                    searchQuery.setCount(totalCount);
                    searchQuery.setLastUpdated(LocalDateTime.now());
                    searchQueryRepository.save(searchQuery);
                }

                count++;

                if (batch.size() >= BATCH_SIZE) {
                    searchQueryRepository.saveAll(batch);
                    System.out.println("Loaded " + count + " queries...");
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                searchQueryRepository.saveAll(batch);
                System.out.println("Loaded final batch of " + batch.size() + " queries");
            }

            System.out.println("Dataset loaded successfully. Total queries: " + count);

        } catch (Exception e) {

            System.err.println("Error loading dataset: " + e.getMessage());
            e.printStackTrace();
        }

        redisTemplate.opsForValue()
                .set("dataset:loaded","true");
    }
}