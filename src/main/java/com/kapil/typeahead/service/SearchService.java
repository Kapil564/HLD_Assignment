package com.kapil.typeahead.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final BatchWriterService batchWriterService;

    public void submitSearch(String query) {

        String normalizedQuery = query.toLowerCase();

        batchWriterService.addToBatch(normalizedQuery, 1);
    }
}
