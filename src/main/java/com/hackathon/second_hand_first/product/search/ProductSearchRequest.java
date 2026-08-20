package com.hackathon.second_hand_first.product.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AI 의 search 노드가 보내는 검색 조건.
 *
 * <p>필드 이름은 AI 쪽 {@code SearchQuery} 와 같은 snake_case 다.
 * 계약은 docs/상품_검색_API_명세.md 1장에 있다.
 */
public record ProductSearchRequest(
        String product,
        Long budget,

        @JsonProperty("used_allowed")
        Boolean usedAllowed,

        Integer limit
) {
    private static final int DEFAULT_LIMIT = 40;

    public boolean usedAllowedOrDefault() {
        // 명시적으로 false 일 때만 새상품만 준다. 없으면 중고를 포함한다 —
        // 이 서비스의 존재 이유가 중고 비교이기 때문이다.
        return usedAllowed == null || usedAllowed;
    }

    public int limitOrDefault() {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
    }
}
