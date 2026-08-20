package com.hackathon.second_hand_first.product.service;

import com.hackathon.second_hand_first.product.domain.Product;
import com.hackathon.second_hand_first.product.domain.ProductCategory;
import com.hackathon.second_hand_first.product.domain.ProductStatus;
import com.hackathon.second_hand_first.product.dto.response.BestDealItemResponse;
import com.hackathon.second_hand_first.product.dto.response.BestDealPageResponse;
import com.hackathon.second_hand_first.search.domain.SearchResult;
import com.hackathon.second_hand_first.search.repository.SearchResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class BestDealService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAGE_SIZE = 100;

    private final SearchResultRepository searchResultRepository;

    @Transactional(readOnly = true)
    public BestDealPageResponse getBestDeals(
            String category,
            String sort,
            int page,
            int size
    ) {
        validatePage(page, size);
        ProductCategory categoryFilter = parseCategory(category);
        SortOrder sortOrder = parseSort(sort);

        List<SearchResult> candidates = searchResultRepository
                .findByCreatedAtGreaterThanEqualAndRecommendationScoreIsNotNull(
                        LocalDate.now(SEOUL).atStartOfDay()
                );

        Map<Long, SearchResult> bestByProduct = new LinkedHashMap<>();
        candidates.stream()
                .filter(this::isDisplayable)
                .filter(result -> categoryFilter == null
                        || result.getProduct().getCategory() == categoryFilter)
                .forEach(result -> bestByProduct.merge(
                        result.getProduct().getId(),
                        result,
                        this::higherScored
                ));

        List<SearchResult> sorted = bestByProduct.values().stream()
                .sorted(comparator(sortOrder))
                .toList();
        int fromIndex = Math.min(page * size, sorted.size());
        int toIndex = Math.min(fromIndex + size, sorted.size());

        List<BestDealItemResponse> content = IntStream.range(fromIndex, toIndex)
                .mapToObj(index -> toResponse(sorted.get(index), index + 1))
                .toList();

        return new BestDealPageResponse(
                content,
                page,
                size,
                sorted.size(),
                toIndex < sorted.size()
        );
    }

    private boolean isDisplayable(SearchResult result) {
        return result.getProduct().getStatus() == ProductStatus.SELLING
                && result.getRecommendationReason() != null
                && !result.getRecommendationReason().isBlank();
    }

    private SearchResult higherScored(SearchResult first, SearchResult second) {
        return score(first) >= score(second) ? first : second;
    }

    private Comparator<SearchResult> comparator(SortOrder sortOrder) {
        Comparator<SearchResult> byScore = Comparator.comparingDouble(this::score).reversed();
        return switch (sortOrder) {
            case AI_RECOMMENDED -> byScore;
            case PRICE_ASC -> Comparator.comparingLong(
                            (SearchResult result) -> result.getProduct().getPrice()
                    )
                    .thenComparing(byScore);
            case SAVINGS_DESC -> Comparator
                    .comparingInt((SearchResult result) -> result.getProduct().calculateSavingsRate())
                    .reversed()
                    .thenComparing(byScore);
        };
    }

    private double score(SearchResult result) {
        return result.getRecommendationScore() == null ? 0.0 : result.getRecommendationScore();
    }

    private BestDealItemResponse toResponse(SearchResult result, int rank) {
        Product product = result.getProduct();
        long officialPrice = product.getReferencePrice() == null
                ? product.getPrice()
                : product.getReferencePrice();
        String imageUrl = product.getImages().isEmpty()
                ? null
                : product.getImages().getFirst().getImageUrl();

        return new BestDealItemResponse(
                product.getId().toString(),
                rank,
                product.getPlatform(),
                displayCategory(product.getCategory()),
                product.getTitle(),
                product.getPrice(),
                officialPrice,
                product.calculateSavingsAmount(),
                product.calculateSavingsRate(),
                product.getCondition(),
                product.getLocation() == null ? "위치 정보 없음" : product.getLocation(),
                result.getRecommendationReason(),
                (int) Math.round(score(result)),
                imageUrl
        );
    }

    private ProductCategory displayCategory(ProductCategory category) {
        return switch (category) {
            case EARPHONES, LAPTOP, SMARTPHONE, SMARTWATCH -> category;
            default -> ProductCategory.OTHER;
        };
    }

    private ProductCategory parseCategory(String category) {
        if (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)) {
            return null;
        }
        try {
            return ProductCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 Best Deal 카테고리입니다.");
        }
    }

    private SortOrder parseSort(String sort) {
        try {
            return SortOrder.valueOf(sort == null ? "AI_RECOMMENDED" : sort.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 Best Deal 정렬 기준입니다.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("페이지는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("페이지 크기는 1에서 100 사이여야 합니다.");
        }
    }

    private enum SortOrder {
        AI_RECOMMENDED,
        PRICE_ASC,
        SAVINGS_DESC
    }
}
