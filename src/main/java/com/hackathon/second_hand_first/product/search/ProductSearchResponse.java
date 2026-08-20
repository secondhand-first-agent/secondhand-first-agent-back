package com.hackathon.second_hand_first.product.search;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 검색 결과. {@code items} 는 <b>통합 스키마 그대로</b>다.
 *
 * <p>필드 정의 SSOT 는 data-analysis/docs/통합_스키마_정의.md 이며,
 * AI 가 그 형태를 전제로 읽는다. 여기서 형태를 바꾸면 AI 쪽이 깨진다.
 *
 * <p><b>실패해도 200 으로 준다.</b> ok 로 성패를 가른다 — AI 가 검색 실패를
 * 정상 흐름으로 다뤄야 «상품이 없다»와 «서비스가 죽었다»를 구분할 수 있다.
 */
public record ProductSearchResponse(
        boolean ok,
        List<JsonNode> items,
        String error
) {
    public static ProductSearchResponse ok(List<JsonNode> items) {
        return new ProductSearchResponse(true, items, null);
    }

    public static ProductSearchResponse fail(String error) {
        return new ProductSearchResponse(false, List.of(), error);
    }
}
