package com.hackathon.second_hand_first.product.search;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 의 search 노드가 부르는 내부 검색 API.
 *
 * <p>프론트가 부르는 API 가 아니다. 그래서 공통 응답 봉투({@code ApiResponse})를
 * 쓰지 않고 AI 와 합의한 형태를 그대로 준다 — 봉투를 씌우면 AI 쪽이 한 겹 더 벗겨야 한다.
 *
 * <p>계약: docs/상품_검색_API_명세.md 1장.
 * AI 쪽 경로 상수: ai/tools.py 의 {@code BACKEND_SEARCH_PATH}.
 *
 * <p>{@code product-search.source} 가 설정됐을 때만 존재한다. 검색 구현체 빈도
 * 같은 조건으로 만들어지므로, 설정하지 않으면 <b>컨트롤러와 구현체가 함께 없다.</b>
 * 한쪽만 남아 기동에 실패하는 일이 없다.
 */
@RestController
@ConditionalOnProperty(name = "product-search.source")
@RequiredArgsConstructor
public class InternalProductSearchController {

    private final ProductSearchService productSearchService;

    /**
     * 실패해도 200 을 준다. {@code ok} 로 성패를 가른다.
     *
     * <p>AI 가 검색 실패를 정상 흐름으로 다뤄야 «상품이 없다»와 «서비스가 죽었다»가
     * 구분된다. 여기서 4xx/5xx 를 주면 그래프가 통째로 실패로 끝난다.
     */
    @PostMapping("/internal/products/search")
    public ProductSearchResponse search(@RequestBody ProductSearchRequest request) {
        return productSearchService.search(request);
    }
}
