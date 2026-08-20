package com.hackathon.second_hand_first.search.controller;

import com.hackathon.second_hand_first.auth.security.CustomUserDetails;
import com.hackathon.second_hand_first.common.response.ApiResponse;
import com.hackathon.second_hand_first.search.dto.request.SearchSessionCreateRequest;
import com.hackathon.second_hand_first.search.dto.response.SearchSessionCreateResponse;
import com.hackathon.second_hand_first.search.dto.response.SearchSessionPageResponse;
import com.hackathon.second_hand_first.search.dto.response.SearchSessionResultsResponse;
import com.hackathon.second_hand_first.search.dto.response.SearchSessionDetailResponse;
import com.hackathon.second_hand_first.search.service.SearchSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SearchSessionController {

    private final SearchSessionService searchSessionService;

    @PostMapping("/search-sessions")
    public ResponseEntity<ApiResponse<SearchSessionCreateResponse>> createSearchSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SearchSessionCreateRequest request
    ) {
        SearchSessionCreateResponse response = searchSessionService.create(
                userDetails.getUserId(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("AI 검색을 완료했습니다.", response)
        );
    }

    /**
     * 지난 검색의 결과를 다시 꺼낸다.
     *
     * <p>응답 형태가 검색 실행 응답과 같다. 프론트가 같은 파싱을 재사용할 수 있게
     * 일부러 맞췄다.
     */
    @GetMapping("/search-sessions/{sessionId}/results")
    public ResponseEntity<ApiResponse<SearchSessionResultsResponse>> getSearchResults(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String sessionId
    ) {
        SearchSessionResultsResponse response = searchSessionService.getResults(
                userDetails.getUserId(),
                sessionId
        );
        return ResponseEntity.ok(ApiResponse.success("검색 결과를 조회했습니다.", response));
    }

    @GetMapping("/users/me/search-sessions")
    public ResponseEntity<ApiResponse<SearchSessionPageResponse>> getRecentSearchSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        SearchSessionPageResponse response = searchSessionService.getRecentSessions(
                userDetails.getUserId(),
                page,
                size
        );
        return ResponseEntity.ok(
                ApiResponse.success("최근 검색 내역을 조회했습니다.", response)
        );
    }

    @GetMapping("/search-sessions/{sessionId}")
    public ResponseEntity<ApiResponse<SearchSessionDetailResponse>> getSearchSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String sessionId
    ) {
        SearchSessionDetailResponse response = searchSessionService.getSession(
                userDetails.getUserId(),
                sessionId
        );
        return ResponseEntity.ok(
                ApiResponse.success("검색 세션을 조회했습니다.", response)
        );
    }
}
