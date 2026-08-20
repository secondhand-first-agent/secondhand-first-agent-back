package com.hackathon.second_hand_first.product.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 상품 검색 구현체를 고른다.
 *
 * <p><b>기본은 꺼짐이다.</b> {@code product-search.source=fixture} 일 때만 빈이 생기고,
 * 그때만 {@code /internal/products/search} 가 동작한다. 설정하지 않으면 지금까지와
 * 똑같이 아무 일도 일어나지 않는다.
 *
 * <p>DB 검색이 준비되면 여기에 {@code havingValue = "jpa"} 인 빈을 하나 더 두고
 * 환경변수만 바꾸면 된다. 부르는 쪽 코드는 그대로다.
 */
@Configuration
public class ProductSearchConfig {

    @Bean
    @ConditionalOnProperty(name = "product-search.source", havingValue = "fixture")
    public ProductSearchService fixtureProductSearchService(ObjectMapper objectMapper) {
        return new FixtureProductSearchService(objectMapper);
    }
}
