package com.hackathon.second_hand_first.search.service;

import com.hackathon.second_hand_first.carbon.dto.CarbonSavingResult;
import com.hackathon.second_hand_first.carbon.service.CarbonSavingService;
import com.hackathon.second_hand_first.location.service.ProductLocationEnrichmentService;
import com.hackathon.second_hand_first.search.application.AiSearchClient;
import com.hackathon.second_hand_first.product.domain.Product;
import com.hackathon.second_hand_first.product.service.ProductUpsertService;
import com.hackathon.second_hand_first.search.domain.SearchResult;
import com.hackathon.second_hand_first.search.domain.SearchMessage;
import com.hackathon.second_hand_first.search.domain.SearchSession;
import com.hackathon.second_hand_first.search.domain.SearchMarketReference;
import com.hackathon.second_hand_first.search.dto.request.SearchSessionCreateRequest;
import com.hackathon.second_hand_first.search.dto.response.RecentSearchSessionResponse;
import com.hackathon.second_hand_first.search.dto.response.SearchResultItemResponse;
import com.hackathon.second_hand_first.search.dto.response.SearchSessionCreateResponse;
import com.hackathon.second_hand_first.search.dto.response.SearchSessionPageResponse;
import com.hackathon.second_hand_first.search.dto.response.SearchSessionResultsResponse;
import com.hackathon.second_hand_first.search.dto.response.SearchSessionDetailResponse;
import com.hackathon.second_hand_first.search.exception.SearchSessionNotFoundException;
import com.hackathon.second_hand_first.search.integration.ai.dto.AiParsedConditionsResponse;
import com.hackathon.second_hand_first.search.integration.ai.dto.AiRecommendedProductResponse;
import com.hackathon.second_hand_first.search.integration.ai.dto.AiSearchRequest;
import com.hackathon.second_hand_first.search.integration.ai.dto.AiUserContext;
import com.hackathon.second_hand_first.search.integration.ai.dto.AiUserLocation;
import com.hackathon.second_hand_first.user.domain.User;
import com.hackathon.second_hand_first.user.repository.UserRepository;
import com.hackathon.second_hand_first.search.integration.ai.dto.AiSearchResponse;
import com.hackathon.second_hand_first.search.repository.SearchSessionRepository;
import com.hackathon.second_hand_first.search.repository.SearchResultRepository;
import com.hackathon.second_hand_first.search.repository.SearchMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.UUID;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchSessionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SearchSessionRepository searchSessionRepository;
    private final SearchResultRepository searchResultRepository;
    private final SearchMessageRepository searchMessageRepository;
    private final AiSearchClient aiSearchClient;
    private final ProductUpsertService productUpsertService;
    private final CarbonSavingService carbonSavingService;
    private final UserRepository userRepository;
    private final ProductLocationEnrichmentService productLocationEnrichmentService;

    @Transactional
    public SearchSessionCreateResponse create(
            Long userId,
            SearchSessionCreateRequest request
    ) {
        String sessionId = generateSessionId();
        String requestId = generateRequestId();
        AiSearchResponse aiResponse = aiSearchClient.search(
                new AiSearchRequest(
                        request.query(),
                        requestId,
                        sessionId,
                        null,
                        buildUserContext(userId)
                )
        );
        validateAiResponse(aiResponse, requestId, sessionId);
        validateRecommendations(aiResponse.products());

        List<AiRecommendedProductResponse> enrichedProducts =
                productLocationEnrichmentService.enrichRecommendations(
                        aiResponse.products()
                );

        AiSearchResponse enrichedResponse = new AiSearchResponse(
                aiResponse.requestId(),
                aiResponse.sessionId(),
                aiResponse.scoring(),
                aiResponse.parsedConditions(),
                aiResponse.assistantMessage(),
                aiResponse.marketReference(),
                aiResponse.totalResultCount(),
                enrichedProducts
        );

        AiParsedConditionsResponse analysis = enrichedResponse.parsedConditions();
        SearchSession session = SearchSession.create(
                sessionId,
                userId,
                request.query()
        );
        session.complete(
                analysis.keyword(),
                analysis.querySummary(),
                aiResponse.assistantMessage(),
                analysis.maxPrice(),
                analysis.priority(),
                analysis.conditions(),
                aiResponse.totalResultCount(),
                aiResponse.scoring().version()
        );
        attachMarketReference(session, aiResponse.marketReference());
        SearchSession saved = searchSessionRepository.save(session);
        searchMessageRepository.save(SearchMessage.create(
                generateMessageId(),
                saved,
                aiResponse.assistantMessage()
        ));
        List<SearchResultItemResponse> recommendations =
                saveSearchResults(saved, enrichedResponse.products());
        return SearchSessionCreateResponse.of(
                saved,
                enrichedResponse,
                recommendations
        );
    }

    public SearchSessionPageResponse getRecentSessions(
            Long userId,
            int page,
            int size
    ) {
        validatePageRequest(page, size);
        Page<RecentSearchSessionResponse> result = searchSessionRepository
                .findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(page, size))
                .map(RecentSearchSessionResponse::from);
        return SearchSessionPageResponse.from(result);
    }

    public SearchSessionDetailResponse getSession(Long userId, String sessionId) {
        SearchSession session = searchSessionRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(SearchSessionNotFoundException::new);
        List<SearchMessage> messages = searchMessageRepository
                .findBySearchSessionIdOrderByCreatedAtAscIdAsc(session.getId());
        return SearchSessionDetailResponse.of(session, messages);
    }

    /**
     * 지난 검색의 결과를 다시 꺼낸다.
     *
     * <p>검색을 실행한 순간의 결과는 {@code search_results} 에 남아 있는데 꺼낼 길이
     * 없었다. 그래서 <b>새로고침하거나 뒤로 가면 목록이 사라졌다.</b>
     *
     * <p>탄소 절감량은 저장하지 않아 여기서 다시 계산한다. 전자기기는 내장 계수라
     * 즉시 나오고, 그 밖은 Climatiq 를 부르지만 결과를 캐시하므로 같은 상품을 여러 번
     * 열어도 한 번만 부른다.
     */
    @Transactional(readOnly = true)
    public SearchSessionResultsResponse getResults(Long userId, String sessionId) {
        SearchSession session = searchSessionRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(SearchSessionNotFoundException::new);

        List<SearchResultItemResponse> recommendations = searchResultRepository
                .findBySearchSessionSessionIdOrderByRankAsc(sessionId)
                .stream()
                .map(this::toResultItem)
                .toList();

        return SearchSessionResultsResponse.of(
                session, assistantMessageOf(session, recommendations.size()), recommendations
        );
    }

    private SearchResultItemResponse toResultItem(SearchResult result) {
        Product product = result.getProduct();
        CarbonSavingResult carbonSaving = carbonSavingService.calculate(
                product.getTitle(),
                product.getCategory(),
                product.getPrice(),
                product.getPlatform(),
                product.getCondition()
        );
        return new SearchResultItemResponse(
                product.getId() != null ? product.getId().toString() : null,
                result.getRank(),
                product.getPlatform(),
                product.getTitle(),
                product.getPrice(),
                firstImageUrl(product),
                result.getRecommendationScore(),
                result.getRecommendationReason(),
                carbonSaving
        );
    }

    /**
     * 대화 기록에 남은 어시스턴트 문구를 그대로 쓴다.
     *
     * <p>«번개장터·중고나라에서 13개 매물을 찾았어요» 같은 문장은 검색 당시 AI 가 만든
     * 것이다. 여기서 새로 만들면 <b>같은 세션인데 문구가 달라진다.</b> 기록이 없을 때만
     * 최소한의 문장을 만든다.
     */
    private String assistantMessageOf(SearchSession session, int resultCount) {
        return searchMessageRepository
                .findBySearchSessionIdOrderByCreatedAtAscIdAsc(session.getId())
                .stream()
                .map(SearchMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .reduce((first, second) -> second)
                .orElseGet(() -> resultCount == 0
                        ? "조건에 맞는 매물을 찾지 못했어요."
                        : "%d개 매물을 찾았어요.".formatted(resultCount));
    }

    /** 대표 이미지. 없으면 null 이다 — 빈 문자열은 «이미지가 있는데 비었다»로 읽힌다. */
    private String firstImageUrl(Product product) {
        return product.getImages() == null || product.getImages().isEmpty()
                ? null
                : product.getImages().getFirst().getImageUrl();
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("요청 값이 올바르지 않습니다.");
        }
    }

    private List<SearchResultItemResponse> saveSearchResults(
            SearchSession session,
            List<AiRecommendedProductResponse> recommendations
    ) {
        if (recommendations == null || recommendations.isEmpty()) {
            return List.of();
        }
        validateRecommendations(recommendations);

        List<SearchResult> results = new ArrayList<>();
        List<SearchResultItemResponse> responseItems = new ArrayList<>();

        for (AiRecommendedProductResponse recommendation : recommendations) {
            Product product = productUpsertService.upsert(recommendation.product());
            results.add(SearchResult.create(
                    session,
                    product,
                    recommendation.rank(),
                    recommendation.recommendationScore(),
                    recommendation.recommendationReason(),
                    recommendation.scoreBreakdown() == null
                            ? null : recommendation.scoreBreakdown().priceScore(),
                    recommendation.scoreBreakdown() == null
                            ? null : recommendation.scoreBreakdown().qualityScore(),
                    recommendation.scoreBreakdown() == null
                            ? null : recommendation.scoreBreakdown().convenienceScore(),
                    recommendation.distanceKm()
            ));

            CarbonSavingResult carbonSaving = carbonSavingService.calculate(
                    recommendation.product().title(),
                    recommendation.product().category(),
                    recommendation.product().price() != null ? recommendation.product().price() : 0L,
                    recommendation.product().platform(),
                    recommendation.product().condition()
            );

            String imageUrl = (recommendation.product().imageUrls() != null
                    && !recommendation.product().imageUrls().isEmpty())
                    ? recommendation.product().imageUrls().get(0)
                    : null;

            responseItems.add(new SearchResultItemResponse(
                    product.getId() != null ? product.getId().toString() : null,
                    recommendation.rank(),
                    recommendation.product().platform(),
                    recommendation.product().title(),
                    recommendation.product().price() != null ? recommendation.product().price() : 0L,
                    imageUrl,
                    recommendation.recommendationScore(),
                    recommendation.recommendationReason(),
                    carbonSaving
            ));
        }

        searchResultRepository.saveAll(results);
        return responseItems;
    }

    /**
     * 사용자 프로필에서 위치를 읽어 AI에 넘길 맥락을 만든다.
     *
     * <p>프론트는 검색할 때 위치를 보내지 않는다. 로그인 사용자이므로 여기서 채운다.
     * 위치를 등록하지 않은 사용자면 null을 준다 — 없는 좌표를 지어내면 AI 쪽에서
     * 엉뚱한 상품이 "가장 가까운 상품"이 된다.
     *
     * <p>편의점 픽업 가능 여부는 아직 수집하지 않아 null로 둔다. 기본값 true로 두면
     * 편의점 반값택배를 포함한 최저 배송비가 쓰이는데, 주변에 그 편의점이 없는
     * 사용자에게는 존재하지 않는 가격이다.
     */
    private AiUserContext buildUserContext(Long userId) {
        return userRepository.findById(userId)
                .map(SearchSessionService::toUserContext)
                .orElse(null);
    }

    private static AiUserContext toUserContext(User user) {
        if (user.getRegion() == null && user.getLatitude() == null) {
            return null;
        }
        return new AiUserContext(
                new AiUserLocation(user.getRegion(), user.getLatitude(), user.getLongitude()),
                null
        );
    }

    private void validateAiResponse(
            AiSearchResponse response,
            String expectedRequestId,
            String expectedSessionId
    ) {
        if (response == null
                || !expectedRequestId.equals(response.requestId())
                || !expectedSessionId.equals(response.sessionId())
                || response.scoring() == null
                || response.scoring().version() == null
                || response.scoring().version().isBlank()
                || response.parsedConditions() == null
                || response.assistantMessage() == null
                || response.assistantMessage().isBlank()
                || response.totalResultCount() < 0
                || response.products() == null
                || response.totalResultCount() != response.products().size()) {
            throw new IllegalArgumentException("AI 검색 응답이 올바르지 않습니다.");
        }
    }

    private void validateRecommendations(List<AiRecommendedProductResponse> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return;
        }

        Set<Integer> ranks = new HashSet<>();
        Set<String> productKeys = new HashSet<>();
        Double previousScore = null;
        int elevenstCount = 0;
        for (int index = 0; index < recommendations.size(); index++) {
            AiRecommendedProductResponse recommendation = recommendations.get(index);
            if (recommendation == null || recommendation.product() == null) {
                throw new IllegalArgumentException("AI 추천 상품 응답이 올바르지 않습니다.");
            }
            if (recommendation.rank() != index + 1) {
                throw new IllegalArgumentException("AI 추천 순위는 1부터 연속된 오름차순이어야 합니다.");
            }
            if (!ranks.add(recommendation.rank())) {
                throw new IllegalArgumentException("AI 추천 순위가 중복되었습니다.");
            }
            Double score = recommendation.recommendationScore();
            if (score == null || score < 0 || score > 100) {
                throw new IllegalArgumentException("AI 추천 점수는 0에서 100 사이여야 합니다.");
            }
            if (previousScore != null && score > previousScore) {
                throw new IllegalArgumentException("AI 추천 순위와 점수 순서가 일치하지 않습니다.");
            }
            previousScore = score;
            String productKey = recommendation.product().platform()
                    + ":" + recommendation.product().externalProductId();
            if (!productKeys.add(productKey)) {
                throw new IllegalArgumentException("AI 추천 상품이 중복되었습니다.");
            }
            if (recommendation.product().platform() == com.hackathon.second_hand_first.product.domain.Platform.ELEVENST) {
                elevenstCount++;
                if (elevenstCount > 1) {
                    throw new IllegalArgumentException("11번가 인기 신품은 하나만 포함할 수 있습니다.");
                }
            }
        }
    }

    private void attachMarketReference(
            SearchSession session,
            com.hackathon.second_hand_first.search.integration.ai.dto.AiMarketReferenceResponse source
    ) {
        if (source == null) {
            session.replaceMarketReference(null);
            return;
        }
        if (source.medianPrice() == null
                || source.sampleCount() == null
                || source.calculatedAt() == null) {
            throw new IllegalArgumentException("AI 시세 기준 응답의 필수 값이 누락되었습니다.");
        }
        session.replaceMarketReference(SearchMarketReference.create(
                session,
                source.productName(),
                source.sourcePlatform(),
                source.sourceName(),
                source.referenceType(),
                source.medianPrice(),
                source.sampleCount(),
                source.calculatedAt().atZoneSameInstant(SEOUL).toLocalDateTime(),
                source.sourceUrl()
        ));
    }

    private String generateSessionId() {
        return "ss_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "");
    }
}
