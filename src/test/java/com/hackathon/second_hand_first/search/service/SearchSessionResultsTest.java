package com.hackathon.second_hand_first.search.service;

import com.hackathon.second_hand_first.product.domain.Platform;
import com.hackathon.second_hand_first.product.domain.Product;
import com.hackathon.second_hand_first.product.domain.ProductCategory;
import com.hackathon.second_hand_first.product.domain.ProductCondition;
import com.hackathon.second_hand_first.product.domain.ProductStatus;
import com.hackathon.second_hand_first.product.repository.ProductRepository;
import com.hackathon.second_hand_first.search.domain.SearchPriority;
import com.hackathon.second_hand_first.search.domain.SearchResult;
import com.hackathon.second_hand_first.search.domain.SearchSession;
import com.hackathon.second_hand_first.search.dto.response.SearchSessionResultsResponse;
import com.hackathon.second_hand_first.search.exception.SearchSessionNotFoundException;
import com.hackathon.second_hand_first.search.repository.SearchResultRepository;
import com.hackathon.second_hand_first.search.repository.SearchSessionRepository;
import com.hackathon.second_hand_first.user.domain.User;
import com.hackathon.second_hand_first.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 지난 검색의 결과를 다시 꺼낼 수 있는지 본다.
 *
 * <p>이 API 가 없으면 <b>새로고침하거나 뒤로 가는 순간 목록이 사라진다.</b>
 * 데이터는 {@code search_results} 에 멀쩡히 있는데 꺼낼 길이 없었다.
 */
@SpringBootTest
@Transactional
class SearchSessionResultsTest {

    @Autowired
    private SearchSessionService searchSessionService;

    @Autowired
    private SearchSessionRepository searchSessionRepository;

    @Autowired
    private SearchResultRepository searchResultRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private String sessionId;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.create(
                "주인", "owner" + System.nanoTime() + "@test.com", "encoded", null, true, false));

        SearchSession session = SearchSession.create(
                "ss_" + System.nanoTime(), owner.getId(), "에어팟 프로 3 25만원 이하"
        );
        session.complete(
                "에어팟 프로 3", "에어팟 프로 3 25만원 이하",
                "번개장터에서 3개 매물을 찾았어요.", 250_000L,
                SearchPriority.BEST_VALUE, List.of(ProductCondition.USED), 3
        );
        SearchSession saved = searchSessionRepository.save(session);
        sessionId = saved.getSessionId();

        for (int rank = 1; rank <= 3; rank++) {
            Product product = productRepository.save(product(rank));
            searchResultRepository.save(SearchResult.create(
                    saved, product, rank, 90.0 - rank,
                    rank == 1 ? "가장 저렴합니다." : null
            ));
        }
    }

    private Product product(int rank) {
        return Product.create(
                Platform.BUNJANG, "ext-" + System.nanoTime(), "에어팟 프로 3 " + rank,
                "설명", ProductCategory.EARPHONES, 100_000L * rank, null,
                ProductCondition.USED, ProductStatus.SELLING, "서울특별시 강남구",
                true, true, null, true,
                "https://m.bunjang.co.kr/products/" + rank, null, null, LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("저장된 결과를 순위대로 돌려준다")
    void returnsStoredResults() {
        SearchSessionResultsResponse response =
                searchSessionService.getResults(owner.getId(), sessionId);

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.resultCount()).isEqualTo(3);
        assertThat(response.recommendations())
                .extracting(item -> item.rank())
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("검색 조건도 함께 온다 — 화면이 «이렇게 이해했어요»를 다시 그린다")
    void includesParsedConditions() {
        SearchSessionResultsResponse response =
                searchSessionService.getResults(owner.getId(), sessionId);

        assertThat(response.parsedConditions()).isNotNull();
        assertThat(response.parsedConditions().keyword()).isEqualTo("에어팟 프로 3");
        assertThat(response.parsedConditions().maxPrice()).isEqualTo(250_000L);
    }

    @Test
    @DisplayName("추천 사유가 없는 항목은 빈 문자열이 아니라 null 이다")
    void keepsMissingReasonNull() {
        SearchSessionResultsResponse response =
                searchSessionService.getResults(owner.getId(), sessionId);

        assertThat(response.recommendations().get(0).recommendationReason()).isNotBlank();
        assertThat(response.recommendations().get(1).recommendationReason()).isNull();
    }

    @Test
    @DisplayName("탄소 절감량을 다시 계산해 채운다 — 저장하지 않는 값이다")
    void recalculatesCarbonSaving() {
        SearchSessionResultsResponse response =
                searchSessionService.getResults(owner.getId(), sessionId);

        assertThat(response.recommendations())
                .allSatisfy(item -> assertThat(item.carbonSaving()).isNotNull());
    }

    @Test
    @DisplayName("남의 세션은 볼 수 없다 — 존재 여부도 알려주지 않는다")
    void deniesOtherUser() {
        User other = userRepository.save(User.create(
                "타인", "other" + System.nanoTime() + "@test.com", "encoded", null, true, false));

        assertThatThrownBy(() -> searchSessionService.getResults(other.getId(), sessionId))
                .isInstanceOf(SearchSessionNotFoundException.class);
    }

    @Test
    @DisplayName("없는 세션도 같은 예외로 끝난다")
    void deniesUnknownSession() {
        assertThatThrownBy(() -> searchSessionService.getResults(owner.getId(), "ss_없는거"))
                .isInstanceOf(SearchSessionNotFoundException.class);
    }
}
