package com.hackathon.second_hand_first.product.service;

import com.hackathon.second_hand_first.product.domain.Platform;
import com.hackathon.second_hand_first.product.domain.Product;
import com.hackathon.second_hand_first.product.domain.ProductCategory;
import com.hackathon.second_hand_first.product.domain.ProductCondition;
import com.hackathon.second_hand_first.product.domain.ProductStatus;
import com.hackathon.second_hand_first.product.dto.response.BestDealPageResponse;
import com.hackathon.second_hand_first.search.domain.SearchResult;
import com.hackathon.second_hand_first.search.repository.SearchResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BestDealServiceTest {

    @Mock
    private SearchResultRepository searchResultRepository;

    private BestDealService bestDealService;

    @BeforeEach
    void setUp() {
        bestDealService = new BestDealService(searchResultRepository);
    }

    @Test
    void 오늘_저장된_AI_추천을_Best_Deal로_반환한다() {
        SearchResult result = recommendation(
                2L, "맥북 에어 M2", ProductCategory.LAPTOP,
                980_000L, 1_300_000L, 84.0, "가격과 상태가 합리적입니다."
        );
        when(searchResultRepository
                .findByCreatedAtGreaterThanEqualAndRecommendationScoreIsNotNull(any(LocalDateTime.class)))
                .thenReturn(List.of(result));

        BestDealPageResponse response = bestDealService.getBestDeals(
                "ALL", "AI_RECOMMENDED", 0, 12
        );

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().productId()).isEqualTo("2");
        assertThat(response.content().getFirst().rank()).isEqualTo(1);
        assertThat(response.content().getFirst().officialPrice()).isEqualTo(1_300_000L);
        assertThat(response.content().getFirst().recommendationScore()).isEqualTo(84);
        assertThat(response.content().getFirst().recommendationReason())
                .isEqualTo("가격과 상태가 합리적입니다.");
    }

    @Test
    void 추천_근거가_없는_결과는_Best_Deal에서_제외한다() {
        Product product = mock(Product.class);
        when(product.getStatus()).thenReturn(ProductStatus.SELLING);
        SearchResult result = mock(SearchResult.class);
        when(result.getProduct()).thenReturn(product);
        when(result.getRecommendationReason()).thenReturn(null);
        when(searchResultRepository
                .findByCreatedAtGreaterThanEqualAndRecommendationScoreIsNotNull(any(LocalDateTime.class)))
                .thenReturn(List.of(result));

        BestDealPageResponse response = bestDealService.getBestDeals(
                "ALL", "AI_RECOMMENDED", 0, 12
        );

        assertThat(response.content()).isEmpty();
    }

    @Test
    void 잘못된_페이지_크기를_거부한다() {
        assertThatThrownBy(() -> bestDealService.getBestDeals(
                "ALL", "AI_RECOMMENDED", 0, 101
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("페이지 크기는 1에서 100 사이여야 합니다.");
    }

    private SearchResult recommendation(
            Long productId,
            String title,
            ProductCategory category,
            long price,
            long referencePrice,
            double score,
            String reason
    ) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);
        when(product.getStatus()).thenReturn(ProductStatus.SELLING);
        when(product.getPlatform()).thenReturn(Platform.JOONGNA);
        when(product.getCategory()).thenReturn(category);
        when(product.getTitle()).thenReturn(title);
        when(product.getPrice()).thenReturn(price);
        when(product.getReferencePrice()).thenReturn(referencePrice);
        when(product.calculateSavingsAmount()).thenReturn(referencePrice - price);
        when(product.calculateSavingsRate()).thenReturn(
                (int) Math.round((double) (referencePrice - price) * 100 / referencePrice)
        );
        when(product.getCondition()).thenReturn(ProductCondition.LIGHTLY_USED);
        when(product.getLocation()).thenReturn("강남");
        when(product.getImages()).thenReturn(List.of());

        SearchResult result = mock(SearchResult.class);
        when(result.getProduct()).thenReturn(product);
        when(result.getRecommendationScore()).thenReturn(score);
        when(result.getRecommendationReason()).thenReturn(reason);
        return result;
    }
}
