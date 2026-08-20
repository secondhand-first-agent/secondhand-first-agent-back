package com.hackathon.second_hand_first.search.dto.response;

import com.hackathon.second_hand_first.search.domain.SearchSession;

import java.util.List;

/**
 * 지난 검색의 결과를 다시 꺼내 준다.
 *
 * <p>필드 구성이 {@link SearchSessionCreateResponse} 와 같다. <b>일부러 같게 뒀다</b> —
 * 프론트가 검색을 실행할 때 쓰던 파싱을 그대로 재사용할 수 있어야 한다. 새로 만들면
 * 화면 코드가 두 벌이 된다.
 *
 * <p>검색을 실행한 순간의 결과는 {@code search_results} 에 남아 있다. 이 API 가 없으면
 * <b>새로고침하거나 뒤로 가는 순간 목록이 사라진다</b> — 데이터는 멀쩡한데 꺼낼 길이
 * 없었다.
 */
public record SearchSessionResultsResponse(
        String sessionId,
        String status,
        ParsedConditionsResponse parsedConditions,
        String assistantMessage,
        int resultCount,
        List<SearchResultItemResponse> recommendations
) {
    public static SearchSessionResultsResponse of(
            SearchSession session,
            String assistantMessage,
            List<SearchResultItemResponse> recommendations
    ) {
        return new SearchSessionResultsResponse(
                session.getSessionId(),
                session.getStatus().name(),
                ParsedConditionsResponse.from(session),
                assistantMessage,
                recommendations.size(),
                recommendations
        );
    }
}
