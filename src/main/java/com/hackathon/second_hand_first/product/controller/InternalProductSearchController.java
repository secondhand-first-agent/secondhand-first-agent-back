package com.hackathon.second_hand_first.product.controller;

import com.hackathon.second_hand_first.product.dto.request.InternalProductSearchRequest;
import com.hackathon.second_hand_first.product.dto.response.InternalProductSearchResponse;
import com.hackathon.second_hand_first.product.service.InternalProductSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class InternalProductSearchController {

    private final InternalProductSearchService internalProductSearchService;

    @Value("${app.internal-api-key:}")
    private String internalApiKey;

    @PostMapping("/internal/products/search")
    public ResponseEntity<InternalProductSearchResponse> search(
            @RequestHeader(value = "X-API-Key", required = false) String providedApiKey,
            @Valid @RequestBody InternalProductSearchRequest request
    ) {
        if (!internalApiKey.isBlank() && !internalApiKey.equals(providedApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 API 키입니다.");
        }
        return ResponseEntity.ok(internalProductSearchService.search(request));
    }
}
