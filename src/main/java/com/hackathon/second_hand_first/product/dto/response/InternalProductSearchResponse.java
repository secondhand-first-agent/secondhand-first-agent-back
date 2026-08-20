package com.hackathon.second_hand_first.product.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hackathon.second_hand_first.product.domain.Platform;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record InternalProductSearchResponse(
        boolean ok,
        List<Item> items,
        String error
) {
    public static InternalProductSearchResponse success(List<Item> items) {
        return new InternalProductSearchResponse(true, items, null);
    }

    public record Item(
            Platform platform,
            @JsonProperty("platform_product_id") String platformProductId,
            String url,
            String title,
            long price,
            String currency,
            String description,
            List<String> images,
            @JsonProperty("condition_level") String conditionLevel,
            @JsonProperty("condition_raw") String conditionRaw,
            @JsonProperty("trade_method") List<String> tradeMethod,
            @JsonProperty("delivery_fee") DeliveryFee deliveryFee,
            Location location,
            @JsonProperty("price_range") Object priceRange,
            @JsonProperty("collected_at") LocalDateTime collectedAt,
            Seller seller
    ) {
    }

    public record DeliveryFee(
            String status,
            String payer,
            @JsonProperty("min_fee") Long minFee,
            @JsonProperty("home_delivery_fee") Long homeDeliveryFee,
            List<Object> options,
            Map<String, Object> raw
    ) {
    }

    public record Location(
            String name,
            @JsonProperty("full_address") String fullAddress,
            String precision,
            List<Region> regions,
            Coordinates coordinates
    ) {
    }

    public record Region(
            String name,
            @JsonProperty("full_address") String fullAddress,
            String code
    ) {
    }

    public record Coordinates(
            double latitude,
            double longitude
    ) {
    }

    public record Seller(
            double rating,
            @JsonProperty("review_count") int reviewCount
    ) {
    }
}
