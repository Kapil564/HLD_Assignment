package com.kapil.typeahead.dto;

import java.util.List;

public record SuggestResponse(
        String query,
        List<SuggestionItem> suggestions
) {
    public record SuggestionItem(
            String text,
            long count
    ) {}
}