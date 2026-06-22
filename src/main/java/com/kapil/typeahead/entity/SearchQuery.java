package com.kapil.typeahead.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_queries")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String queryText;

    private Long count;

    private LocalDateTime lastUpdated;

    private LocalDateTime lastSearchedAt;
}
