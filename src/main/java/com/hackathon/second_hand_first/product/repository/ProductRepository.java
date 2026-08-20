package com.hackathon.second_hand_first.product.repository;

import com.hackathon.second_hand_first.product.domain.Platform;
import com.hackathon.second_hand_first.product.domain.Product;
import com.hackathon.second_hand_first.product.domain.ProductStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByPlatformAndExternalProductId(
            Platform platform,
            String externalProductId
    );

    boolean existsByPlatformAndExternalProductId(
            Platform platform,
            String externalProductId
    );

    @EntityGraph(attributePaths = {"images", "sellerSnapshot"})
    List<Product> findDistinctByStatus(ProductStatus status);

    @EntityGraph(attributePaths = {"images", "sellerSnapshot"})
    @Query("select distinct product from Product product where product.id = :productId")
    Optional<Product> findDetailById(@Param("productId") Long productId);

    @EntityGraph(attributePaths = {"images", "sellerSnapshot"})
    @Query("""
            select distinct product
            from Product product
            where product.status = :status
              and lower(product.title) like lower(concat('%', :keyword, '%'))
              and (:platform is null or product.platform = :platform)
            order by product.price asc
            """)
    List<Product> searchForAi(
            @Param("keyword") String keyword,
            @Param("status") ProductStatus status,
            @Param("platform") Platform platform,
            Pageable pageable
    );
}
