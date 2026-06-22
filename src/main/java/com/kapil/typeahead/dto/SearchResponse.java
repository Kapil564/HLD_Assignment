package com.kapil.typeahead.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResponse {

    private String message;
    private String result;

    public SearchResponse(String message) {
        this.message = message;
    }
}
