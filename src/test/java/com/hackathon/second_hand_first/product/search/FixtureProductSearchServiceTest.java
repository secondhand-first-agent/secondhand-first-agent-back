package com.hackathon.second_hand_first.product.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureProductSearchServiceTest {

    private FixtureProductSearchService service;

    @BeforeEach
    void setUp() {
        service = new FixtureProductSearchService(JsonMapper.builder().build());
        service.loadFixtures();
    }

    @Test
    @DisplayName("픽스처를 읽어 상품을 담는다")
    void loadsFixtures() {
        ProductSearchResponse response = service.search(
                new ProductSearchRequest("에어팟", null, true, 100)
        );

        assertThat(response.ok()).isTrue();
        assertThat(response.items()).isNotEmpty();
    }

    @Test
    @DisplayName("items 를 통합 스키마 그대로 돌려준다 — AI 가 그 형태를 전제로 읽는다")
    void keepsUnifiedSchema() {
        JsonNode item = service.search(
                new ProductSearchRequest("에어팟", null, true, 1)
        ).items().getFirst();

        // 매핑하지 않고 원본을 그대로 넘기므로 이 필드들이 살아 있어야 한다.
        assertThat(item.has("platform")).isTrue();
        assertThat(item.has("platform_product_id")).isTrue();
        assertThat(item.has("condition_level")).isTrue();
        assertThat(item.has("delivery_fee")).isTrue();
        assertThat(item.has("location")).isTrue();
        assertThat(item.has("trade_method")).isTrue();
    }

    @Test
    @DisplayName("띄어쓰기가 달라도 제목에 걸린다")
    void ignoresSpacing() {
        List<JsonNode> spaced = service.search(
                new ProductSearchRequest("에어팟 프로", null, true, 100)
        ).items();
        List<JsonNode> joined = service.search(
                new ProductSearchRequest("에어팟프로", null, true, 100)
        ).items();

        assertThat(spaced).isNotEmpty();
        assertThat(joined).hasSameSizeAs(spaced);
    }

    @Test
    @DisplayName("예산을 하드 필터로 쓰지 않는다 — 근소한 초과는 남긴다")
    void budgetIsSoft() {
        long budget = 200_000L;

        List<JsonNode> items = service.search(
                new ProductSearchRequest("에어팟", budget, true, 100)
        ).items();

        assertThat(items).isNotEmpty();
        // 20% 까지는 허용하고, 그 위는 자른다.
        assertThat(items).allSatisfy(item ->
                assertThat(item.path("price").asLong()).isLessThanOrEqualTo((long) (budget * 1.2))
        );
        boolean hasOverBudget = items.stream()
                .anyMatch(item -> item.path("price").asLong() > budget);
        assertThat(hasOverBudget)
                .as("예산을 조금 넘는 후보도 함께 준다")
                .isTrue();
    }

    @Test
    @DisplayName("used_allowed=false 면 새상품만 남는다")
    void newOnly() {
        List<JsonNode> items = service.search(
                new ProductSearchRequest("에어팟", null, false, 100)
        ).items();

        assertThat(items).isNotEmpty();
        assertThat(items).allSatisfy(item -> assertThat(
                item.path("condition_level").asString("").equals("NEW")
                        || item.path("platform").asString("").equals("ELEVENST")
        ).isTrue());
    }

    @Test
    @DisplayName("싼 순으로 limit 만큼 자른다")
    void sortsByPriceAndLimits() {
        List<JsonNode> items = service.search(
                new ProductSearchRequest("에어팟", null, true, 3)
        ).items();

        assertThat(items).hasSize(3);
        assertThat(items.get(0).path("price").asLong())
                .isLessThanOrEqualTo(items.get(1).path("price").asLong());
        assertThat(items.get(1).path("price").asLong())
                .isLessThanOrEqualTo(items.get(2).path("price").asLong());
    }

    @Test
    @DisplayName("없는 상품은 0건이지만 실패가 아니다")
    void emptyIsNotFailure() {
        ProductSearchResponse response = service.search(
                new ProductSearchRequest("존재하지않는상품명xyz", null, true, 40)
        );

        assertThat(response.ok())
                .as("«상품이 없다»와 «검색이 실패했다»는 다르다")
                .isTrue();
        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("product 가 비면 실패로 돌려준다")
    void blankProductFails() {
        ProductSearchResponse response = service.search(
                new ProductSearchRequest("  ", null, true, 40)
        );

        assertThat(response.ok()).isFalse();
        assertThat(response.error()).isNotBlank();
    }
}
