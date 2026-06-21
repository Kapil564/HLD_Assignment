package com.kapil.typeahead.dto;

import java.util.List;

public record SuggestResponse(
        String prefix,
        List<String> suggestions
) {
}