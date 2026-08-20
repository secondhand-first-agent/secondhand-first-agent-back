package com.hackathon.second_hand_first.product.search;

/**
 * 조건에 맞는 상품 후보를 찾는다.
 *
 * <p>지금 구현은 {@link FixtureProductSearchService} 하나다 — 미리 수집해 둔
 * 크롤러 결과를 메모리에서 고른다. DB 검색이 준비되면 <b>이 인터페이스에
 * 구현체만 갈아끼우면 된다.</b>
 */
public interface ProductSearchService {

    ProductSearchResponse search(ProductSearchRequest request);
}
