package com.uet.agritech.order

import com.uet.agritech.order.dto.CategoryRevenueDto
import com.uet.agritech.product.Product
import com.uet.agritech.user.User
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long> {
    fun findAllByOrder(order: Order): List<OrderItem>

    fun findAllByProductFarmer(farmer: User): List<OrderItem>

    @Query("""
        SELECT oi2.product, COUNT(oi2.product) as freq 
        FROM OrderItem oi1 
        JOIN OrderItem oi2 ON oi1.order = oi2.order 
        WHERE oi1.product = :currentProduct 
          AND oi2.product != :currentProduct 
        GROUP BY oi2.product 
        ORDER BY freq DESC
    """)
    fun findFrequentlyBoughtTogether(currentProduct: Product, pageable: Pageable): List<Array<Any>>

    @Query("""
        SELECT SUM(oi.quantity * oi.price) 
        FROM OrderItem oi 
        WHERE oi.product.farmer = :farmer 
        AND oi.order.status = 'COMPLETED'
    """)
    fun getTotalRevenueByFarmer(farmer: User): Double?

    @Query("""
        SELECT new com.uet.agritech.order.dto.CategoryRevenueDto(oi.product.category, SUM(oi.quantity * oi.price))
        FROM OrderItem oi 
        WHERE oi.product.farmer = :farmer 
        AND oi.order.status = 'COMPLETED'
        GROUP BY oi.product.category
    """)
    fun getRevenueByCategory(farmer: User): List<CategoryRevenueDto>

    @Query("""
    SELECT COUNT(oi)
    FROM OrderItem oi
    WHERE oi.order.user.phoneNumber = :phone
      AND oi.product.id = :productId
      AND oi.order.status = 'COMPLETED'
""")
    fun countCompletedPurchases(
        @Param("phone") phone: String,
        @Param("productId") productId: String
    ): Long

    @Query(
        """
        SELECT oi
        FROM OrderItem oi
        JOIN FETCH oi.product
        JOIN FETCH oi.order o
        WHERE o.user = :buyer
          AND o.status = 'COMPLETED'
          AND o.orderDate >= :start
          AND o.orderDate < :end
        """
    )
    fun findCompletedItemsForSpending(
        @Param("buyer") buyer: User,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<OrderItem>

    @EntityGraph(attributePaths = ["product", "product.imageUrls"])
    @Query(
        """
    SELECT DISTINCT i FROM OrderItem i
    WHERE i.order.id IN :orderIds
    ORDER BY i.id ASC
    """
    )
    fun findForAdminOrderIds(
        @Param("orderIds") orderIds: List<Long>
    ): List<OrderItem>
}