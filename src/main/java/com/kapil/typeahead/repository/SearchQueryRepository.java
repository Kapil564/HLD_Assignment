package com.kapil.typeahead.repository;

import com.kapil.typeahead.entity.SearchQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, Long> {

    Optional<SearchQuery> findByQueryText(String queryText);

    List<SearchQuery> findByQueryTextIn(Collection<String> queryTexts);

    @Modifying
    @Query("UPDATE SearchQuery s SET s.count = s.count + :delta, s.lastUpdated = CURRENT_TIMESTAMP WHERE s.queryText = :queryText")
    void incrementCount(@Param("queryText") String queryText, @Param("delta") long delta);

    @Modifying
    @Query("UPDATE SearchQuery s SET s.lastSearchedAt = CURRENT_TIMESTAMP WHERE s.queryText = :queryText")
    void updateLastSearchedAt(@Param("queryText") String queryText);

    @Query("SELECT s.queryText, s.count FROM SearchQuery s")
    List<Object[]> findAllQueriesAndCounts();
}
