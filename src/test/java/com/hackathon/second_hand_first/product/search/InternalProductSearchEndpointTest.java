package com.hackathon.second_hand_first.product.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배포 환경에서 AI 가 실제로 부를 수 있는지 본다.
 *
 * <p>단위 테스트로는 «빈이 만들어지는가»와 «인증 없이 통과하는가»를 확인할 수 없다.
 * 이 둘이 틀리면 배포 후에야 알게 된다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "product-search.source=fixture"
)
class InternalProductSearchEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ProductSearchService productSearchService;

    @Test
    @DisplayName("픽스처 설정이 있으면 검색 구현체가 만들어진다")
    void beanExists() {
        assertThat(productSearchService).isInstanceOf(FixtureProductSearchService.class);
    }

    @Test
    @DisplayName("로그인 없이 검색 API 를 부를 수 있다 — AI 는 사용자 토큰이 없다")
    void callableWithoutAuth() {
        JsonNode body = RestClient.create()
                .post()
                .uri("http://localhost:" + port + "/internal/products/search")
                .body(new ProductSearchRequest("에어팟", 300_000L, true, 5))
                .retrieve()
                .body(JsonNode.class);

        assertThat(body).isNotNull();
        assertThat(body.path("ok").asBoolean()).isTrue();
        assertThat(body.path("items").isArray()).isTrue();
        assertThat(body.path("items")).isNotEmpty();
        // AI 가 통합 스키마를 그대로 읽는다.
        assertThat(body.path("items").get(0).has("platform_product_id")).isTrue();
    }

    @Test
    @DisplayName("다른 API 는 여전히 인증을 요구한다")
    void othersStillProtected() {
        JsonNode body = RestClient.create()
                .get()
                .uri("http://localhost:" + port + "/users/me")
                .retrieve()
                .onStatus(status -> true, (request, response) -> {
                })
                .body(JsonNode.class);

        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean())
                .as("/internal/** 만 열려야 한다")
                .isFalse();
    }
}
