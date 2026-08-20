package com.hackathon.second_hand_first.product.search;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 미리 수집해 둔 크롤러 결과에서 후보를 고른다. <b>임시 구현이다.</b>
 *
 * <p>존재 이유는 하나다 — <b>DB 적재를 기다리지 않고 전 구간이 도는지 보는 것.</b>
 * 프론트 → 백엔드 → AI(Bedrock 6회) → 백엔드 → 프론트 가 실제로 이어지는지
 * 확인하는 데 상품 데이터의 출처는 중요하지 않다.
 *
 * <p>DB 검색이 준비되면 이 클래스는 지운다. {@link ProductSearchService} 를
 * 구현하므로 부르는 쪽은 바뀌지 않는다.
 *
 * <p>픽스처는 jar 에 함께 빌드된다. 서버에 파일을 올리거나 DB 를 채울 필요가 없다.
 */
public class FixtureProductSearchService implements ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(FixtureProductSearchService.class);

    private static final String FIXTURE_PATTERN = "classpath:fixtures/products/*.json";

    /**
     * 예산을 넘겨도 이 배수까지는 후보에 넣는다.
     *
     * <p><b>예산을 하드 필터로 쓰지 않는다.</b> 30만원인데 결과가 전부 31만원이면
     * 0건보다 근소한 초과를 보여주는 편이 낫다 — "조금만 보태면 이게 있습니다"가
     * 가능한 답이기 때문이다. 최종 판단은 AI 의 재랭킹에 맡긴다.
     */
    private static final double BUDGET_TOLERANCE = 1.2;

    private static final String PLATFORM_NEW_GOODS = "ELEVENST";
    private static final String CONDITION_NEW = "NEW";

    private final ObjectMapper objectMapper;
    private final List<JsonNode> catalog = new ArrayList<>();

    public FixtureProductSearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadFixtures() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(FIXTURE_PATTERN);
            for (Resource resource : resources) {
                try (InputStream input = resource.getInputStream()) {
                    JsonNode file = objectMapper.readTree(input);
                    file.path("items").forEach(catalog::add);
                }
            }
            log.info("픽스처 상품 {}건 적재 — 파일 {}개", catalog.size(), resources.length);
        } catch (Exception exception) {
            // 픽스처를 못 읽어도 서버는 떠야 한다. 검색이 0건이 될 뿐이다.
            log.warn("픽스처를 읽지 못했다 — 검색이 0건으로 동작한다", exception);
        }
    }

    @Override
    public ProductSearchResponse search(ProductSearchRequest request) {
        if (request == null || request.product() == null || request.product().isBlank()) {
            return ProductSearchResponse.fail("product 가 비어 있습니다.");
        }

        List<String> tokens = tokenize(request.product());
        List<JsonNode> matched = catalog.stream()
                .filter(item -> matchesKeyword(item, tokens))
                .filter(item -> withinBudget(item, request.budget()))
                .filter(item -> allowedCondition(item, request.usedAllowedOrDefault()))
                // 재랭킹은 AI 가 한다. 여기서는 값이 싼 순으로만 잘라 준다 —
                // limit 에 걸려 잘릴 때 비싼 것부터 사라지는 편이 덜 아쉽다.
                .sorted(Comparator.comparingLong(this::priceOf))
                .limit(request.limitOrDefault())
                .toList();

        log.info(
                "픽스처 검색 — product={} budget={} used_allowed={} → {}건",
                request.product(), request.budget(), request.usedAllowedOrDefault(), matched.size()
        );
        return ProductSearchResponse.ok(matched);
    }

    /**
     * 검색어를 토막 내 제목에 모두 들어 있는지 본다.
     *
     * <p>"에어팟 프로 3" 이 "애플 에어팟 프로 3세대 정품" 에 걸리도록 공백을 지우고
     * 비교한다. 형태소 분석 없이도 데모 규모에서는 충분하다.
     */
    private boolean matchesKeyword(JsonNode item, List<String> tokens) {
        String title = normalize(item.path("title").asString(""));
        return tokens.stream().allMatch(title::contains);
    }

    private boolean withinBudget(JsonNode item, Long budget) {
        if (budget == null) {
            return true;
        }
        return priceOf(item) <= budget * BUDGET_TOLERANCE;
    }

    /**
     * {@code used_allowed = false} 면 새상품만 남긴다.
     *
     * <p>11번가라서가 아니라 <b>상태값으로 판정한다.</b> 중고 플랫폼에도 미개봉
     * 새상품이 실제로 올라온다 — 플랫폼만으로 자르면 그런 매물이 빠진다.
     */
    private boolean allowedCondition(JsonNode item, boolean usedAllowed) {
        if (usedAllowed) {
            return true;
        }
        return CONDITION_NEW.equals(item.path("condition_level").asString(""))
                || PLATFORM_NEW_GOODS.equals(item.path("platform").asString(""));
    }

    private long priceOf(JsonNode item) {
        return item.path("price").asLong(Long.MAX_VALUE);
    }

    private List<String> tokenize(String value) {
        return List.of(value.trim().split("\\s+")).stream()
                .map(this::normalize)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.KOREAN).replaceAll("\\s+", "");
    }
}
