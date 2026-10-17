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

    fun findByHiddenFalse(pageable: Pageable): Page<Product>

    fun findByFarmerIdAndHiddenFalse(
        farmerId: String,
        pageable: Pageable
    ): Page<Product>

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
            WHERE hidden = false
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