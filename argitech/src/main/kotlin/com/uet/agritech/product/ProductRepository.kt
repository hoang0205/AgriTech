package com.uet.agritech.product

import com.uet.agritech.user.User
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ProductRepository : JpaRepository<Product, String> {

    @Query(
        value = """
            SELECT p.*,
                CASE WHEN u.pickup_address IS NOT NULL AND TRIM(u.pickup_address) <> ''
                    AND u.pickup_latitude BETWEEN -90 AND 90
                    AND u.pickup_longitude BETWEEN -180 AND 180
                THEN 12742 * ASIN(SQRT(LEAST(1.0, GREATEST(0.0,
                    POWER(SIN(RADIANS(u.pickup_latitude - :latitude) / 2), 2)
                    + COS(RADIANS(:latitude)) * COS(RADIANS(u.pickup_latitude))
                    * POWER(SIN(RADIANS(u.pickup_longitude - :longitude) / 2), 2)
                )))) ELSE NULL END AS delivery_distance_km
            FROM products p JOIN users u ON u.id = p.farmer_id
            WHERE p.hidden = false
                AND p.farmer_id <> :buyerId
                AND (:category IS NULL OR p.category = :category)
                AND (:pattern IS NULL OR LOWER(p.name) LIKE LOWER(:pattern) ESCAPE '!')
            ORDER BY CASE WHEN p.quantity > 0 THEN 0 ELSE 1 END,
                CASE WHEN delivery_distance_km IS NULL THEN 1 ELSE 0 END,
                delivery_distance_km ASC, p.id ASC
        """,
        countQuery = """
            SELECT COUNT(*) FROM products p JOIN users u ON u.id = p.farmer_id
            WHERE p.hidden = false
                AND p.farmer_id <> :buyerId
                AND (:category IS NULL OR p.category = :category)
                AND (:pattern IS NULL OR LOWER(p.name) LIKE LOWER(:pattern) ESCAPE '!')
        """,
        nativeQuery = true
    )
    fun findProductsByDistance(
        @Param("buyerId") buyerId: String,
        @Param("latitude") latitude: Double,
        @Param("longitude") longitude: Double,
        @Param("category") category: String?,
        @Param("pattern") pattern: String?,
        pageable: Pageable
    ): Page<Product>

    @Query("SELECT p FROM Product p WHERE p.hidden = false ORDER BY CASE WHEN p.quantity > 0 THEN 0 ELSE 1 END, p.id ASC")
    fun findByHiddenFalse(pageable: Pageable): Page<Product>

    @Query("SELECT p FROM Product p WHERE p.hidden = false AND p.farmer.id = :farmerId ORDER BY CASE WHEN p.quantity > 0 THEN 0 ELSE 1 END, p.id DESC")
    fun findByFarmerIdAndHiddenFalse(
        farmerId: String,
        pageable: Pageable
    ): Page<Product>

    @Query("SELECT p FROM Product p WHERE p.hidden = false AND p.category = :category ORDER BY CASE WHEN p.quantity > 0 THEN 0 ELSE 1 END, p.id DESC")
    fun findByCategoryAndHiddenFalse(
        category: String,
        pageable: Pageable
    ): Page<Product>

    fun findByHiddenFalseAndNameContainingIgnoreCase(
        name: String
    ): List<Product>

    fun findByHiddenFalseAndNameContainingIgnoreCase(
        name: String,
        pageable: Pageable
    ): Page<Product>

    @Query(
        value = """
            SELECT * FROM products
            WHERE hidden = false AND quantity > 0
            ORDER BY RAND()
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findRandomProducts(
        @Param("limit") limit: Int
    ): List<Product>

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.hidden = false
          AND LOWER(p.name) LIKE LOWER(:pattern) ESCAPE '!'
          AND (:farmerId IS NULL OR p.farmer.id = :farmerId)
        ORDER BY CASE WHEN p.quantity > 0 THEN 0 ELSE 1 END, p.id DESC
        """
    )
    fun searchProducts(
        @Param("pattern") pattern: String,
        @Param("farmerId") farmerId: String?,
        pageable: Pageable
    ): Page<Product>

    @Query(
        """
        SELECT DISTINCT p.name FROM Product p
        WHERE p.hidden = false
          AND LOWER(p.name) LIKE LOWER(:pattern) ESCAPE '!'
          AND (:farmerId IS NULL OR p.farmer.id = :farmerId)
        ORDER BY p.name ASC
        """
    )
    fun suggestProductNames(
        @Param("pattern") pattern: String,
        @Param("farmerId") farmerId: String?,
        pageable: Pageable
    ): List<String>

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.hidden = false
          AND p.category = :category
          AND p.id NOT IN :viewedProductIds
        ORDER BY RAND()
        """
    )
    fun findByCategoryNotInProductIds(
        @Param("category") category: String,
        @Param("viewedProductIds") viewedProductIds: List<String>,
        pageable: Pageable
    ): List<Product>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    fun findForReport(
        @Param("id") id: String
    ): Optional<Product>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    fun findForStockUpdate(
        @Param("id") id: String
    ): Optional<Product>

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.hidden = false
          AND p.category = :category
          AND p.quantity > 0
          AND NOT EXISTS (
              SELECT i.id
              FROM UserInteraction i
              WHERE i.user = :user
                AND i.product = p
                AND i.actionType IN ('VIEW', 'ADD_TO_CART', 'PURCHASE')
          )
        ORDER BY p.rating DESC, p.reviewCount DESC, p.id ASC
        """
    )
    fun findUnseenAvailableProductsByCategory(
        @Param("category") category: String,
        @Param("user") user: User,
        pageable: Pageable
    ): List<Product>

    @Query(
        """
        SELECT p FROM Product p
        WHERE p.hidden = false
          AND p.quantity > 0
        ORDER BY p.rating DESC, p.reviewCount DESC, p.id ASC
        """
    )
    fun findAvailableRecommendationFallback(
        pageable: Pageable
    ): List<Product>

    fun findAllByFarmerOrderByNameAsc(
        farmer: User
    ): List<Product>
}
