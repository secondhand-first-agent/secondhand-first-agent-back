package com.hackathon.second_hand_first.product.service;

import com.hackathon.second_hand_first.product.domain.Platform;
import com.hackathon.second_hand_first.product.domain.ProductStatus;
import com.hackathon.second_hand_first.product.dto.request.InternalProductSearchRequest;
import com.hackathon.second_hand_first.product.dto.response.InternalProductSearchResponse;
import com.hackathon.second_hand_first.product.repository.ProductRepository;
import com.hackathon.second_hand_first.product.support.ProductFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalProductSearchServiceTest {

    @Mock
    private ProductRepository productRepository;

    private InternalProductSearchService service;

    @BeforeEach
    void setUp() {
        service = new InternalProductSearchService(productRepository);
    }

    @Test
    void 판매중_상품을_AI_통합_스키마로_변환한다() {
        when(productRepository.searchForAi(
                eq("AirPods"),
                eq(ProductStatus.SELLING),
                isNull(),
                any(Pageable.class)
        )).thenReturn(List.of(ProductFixture.airPodsPro2()));

        InternalProductSearchResponse response = service.search(
                new InternalProductSearchRequest("AirPods", 200_000L, true, 40)
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.error()).isNull();
        assertThat(response.items()).hasSize(1);
        InternalProductSearchResponse.Item item = response.items().getFirst();
        assertThat(item.platform()).isEqualTo(Platform.NAVER_FLEAMARKET);
        assertThat(item.platformProductId()).isEqualTo("mock_1");
        assertThat(item.conditionLevel()).isEqualTo("LIKE_NEW");
        assertThat(item.tradeMethod()).containsExactly("PARCEL", "MEET");
        assertThat(item.deliveryFee().minFee()).isEqualTo(3_000L);
        assertThat(item.images()).hasSize(2);
    }

    @Test
    void 중고를_허용하지_않으면_11번가만_조회한다() {
        when(productRepository.searchForAi(
                eq("에어팟"),
                eq(ProductStatus.SELLING),
                eq(Platform.ELEVENST),
                any(Pageable.class)
        )).thenReturn(List.of());

        service.search(new InternalProductSearchRequest("에어팟", null, false, null));

        verify(productRepository).searchForAi(
                eq("에어팟"),
                eq(ProductStatus.SELLING),
                eq(Platform.ELEVENST),
                any(Pageable.class)
        );
    }
}
