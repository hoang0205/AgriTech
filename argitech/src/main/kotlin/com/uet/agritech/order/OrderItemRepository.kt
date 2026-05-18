package com.uet.agritech.order

import com.uet.agritech.product.Product
import com.uet.agritech.user.User
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

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
}