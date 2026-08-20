package com.hackathon.second_hand_first.product.service;

import com.hackathon.second_hand_first.product.domain.DeliveryFee;
import com.hackathon.second_hand_first.product.domain.Platform;
import com.hackathon.second_hand_first.product.domain.Product;
import com.hackathon.second_hand_first.product.domain.ProductStatus;
import com.hackathon.second_hand_first.product.domain.ProductTradeRegion;
import com.hackathon.second_hand_first.product.domain.SellerSnapshot;
import com.hackathon.second_hand_first.product.dto.request.InternalProductSearchRequest;
import com.hackathon.second_hand_first.product.dto.response.InternalProductSearchResponse;
import com.hackathon.second_hand_first.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InternalProductSearchService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public InternalProductSearchResponse search(InternalProductSearchRequest request) {
        Platform requiredPlatform = request.allowsUsedProducts() ? null : Platform.ELEVENST;
        List<InternalProductSearchResponse.Item> items = productRepository.searchForAi(
                        request.product().trim(),
                        ProductStatus.SELLING,
                        requiredPlatform,
                        PageRequest.of(0, request.effectiveLimit())
                ).stream()
                .map(this::toItem)
                .toList();

        return InternalProductSearchResponse.success(items);
    }

    private InternalProductSearchResponse.Item toItem(Product product) {
        return new InternalProductSearchResponse.Item(
                product.getPlatform(),
                product.getExternalProductId(),
                product.getPlatformUrl(),
                product.getTitle(),
                product.getPrice(),
                "KRW",
                product.getDescription(),
                product.getImages().stream().map(image -> image.getImageUrl()).toList(),
                product.getCondition().name(),
                product.getCondition().name(),
                tradeMethods(product),
                deliveryFee(product),
                location(product),
                null,
                product.getLastRefreshedAt(),
                seller(product.getSellerSnapshot())
        );
    }

    private List<String> tradeMethods(Product product) {
        List<String> methods = new ArrayList<>(2);
        if (product.isShippingAvailable()) {
            methods.add("PARCEL");
        }
        if (product.isDirectTradeAvailable()) {
            methods.add("MEET");
        }
        return List.copyOf(methods);
    }

    private InternalProductSearchResponse.DeliveryFee deliveryFee(Product product) {
        DeliveryFee fee = product.getDeliveryFee();
        String status = product.isShippingAvailable() ? "AVAILABLE" : "NOT_AVAILABLE";
        return new InternalProductSearchResponse.DeliveryFee(
                status,
                fee == null || fee.getPayer() == null ? null : fee.getPayer().name(),
                fee == null ? null : fee.getMinFee(),
                fee == null ? null : fee.getHomeFee(),
                List.of(),
                Map.of()
        );
    }

    private InternalProductSearchResponse.Location location(Product product) {
        List<InternalProductSearchResponse.Region> regions = product.getTradeRegions().stream()
                .map(this::region)
                .toList();
        if (regions.isEmpty() && product.getLocation() != null) {
            regions = List.of(new InternalProductSearchResponse.Region(
                    product.getLocation(), product.getLocation(), null
            ));
        }

        InternalProductSearchResponse.Coordinates coordinates = null;
        if (product.getLatitude() != null && product.getLongitude() != null) {
            coordinates = new InternalProductSearchResponse.Coordinates(
                    product.getLatitude(), product.getLongitude()
            );
        }

        return new InternalProductSearchResponse.Location(
                product.getLocation(),
                product.getLocation(),
                product.getLocation() == null ? "NONE" : "FULL",
                regions,
                coordinates
        );
    }

    private InternalProductSearchResponse.Region region(ProductTradeRegion region) {
        return new InternalProductSearchResponse.Region(
                region.getName(), region.getFullAddress(), region.getCode()
        );
    }

    private InternalProductSearchResponse.Seller seller(SellerSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new InternalProductSearchResponse.Seller(
                snapshot.getTrustScore() / 20.0,
                snapshot.getTradeCount()
        );
    }
}
