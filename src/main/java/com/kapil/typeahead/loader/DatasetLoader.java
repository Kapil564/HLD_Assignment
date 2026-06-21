package com.kapil.typeahead.loader;

import com.kapil.typeahead.model.SearchMetadata;
import com.kapil.typeahead.store.SearchStore;
import com.kapil.typeahead.trie.Trie;
import com.opencsv.CSVReader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class DatasetLoader {

    private final Trie trie;

    private final StringRedisTemplate redisTemplate;
    private final SearchStore searchStore;
    @PostConstruct
    public void load() {

        if(redisTemplate.hasKey("dataset:loaded")) {

            System.out.println(
                    "Dataset already loaded. Skipping."
            );

            return;
        }

        try (CSVReader reader = new CSVReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader()
                        .getResourceAsStream("dataset.csv"))
        ))) {

            String[] header = reader.readNext();

            String[] row;
            int count = 0;

            while ((row = reader.readNext()) != null) {

                String query = row[0];
                long totalCount = Long.parseLong(row[1]);

                trie.insert(query.toLowerCase());
                searchStore.put(query.toLowerCase(), totalCount);

                count++;

                if (count % 10000 == 0) {
                    System.out.println("Loaded " + count + " queries...");
                }
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