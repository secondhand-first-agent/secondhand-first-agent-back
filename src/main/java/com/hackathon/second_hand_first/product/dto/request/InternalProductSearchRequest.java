package com.hackathon.second_hand_first.product.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record InternalProductSearchRequest(
        @NotBlank
        String product,

        @PositiveOrZero
        Long budget,

        @JsonProperty("used_allowed")
        Boolean usedAllowed,

        @Positive
        @Max(200)
        Integer limit
) {
    public boolean allowsUsedProducts() {
        return usedAllowed == null || usedAllowed;
    }

    public int effectiveLimit() {
        return limit == null ? 40 : limit;
    }
}
