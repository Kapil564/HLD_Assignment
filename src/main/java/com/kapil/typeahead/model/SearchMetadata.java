package com.kapil.typeahead.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchMetadata {

    private String query;

    private long totalCount;

    private long recentCount;
}