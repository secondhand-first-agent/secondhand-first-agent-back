package com.hackathon.second_hand_first.search.repository;

import com.hackathon.second_hand_first.search.domain.SearchResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SearchResultRepository extends JpaRepository<SearchResult, Long> {

    @EntityGraph(attributePaths = {"product", "product.images", "product.sellerSnapshot"})
    List<SearchResult> findBySearchSessionSessionIdOrderByRankAsc(String sessionId);

    @EntityGraph(attributePaths = {"product", "product.images"})
    List<SearchResult> findByCreatedAtGreaterThanEqualAndRecommendationScoreIsNotNull(
            LocalDateTime createdAt
    );
}
