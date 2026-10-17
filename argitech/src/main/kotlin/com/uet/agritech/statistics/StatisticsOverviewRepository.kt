package com.uet.agritech.statistics

import com.uet.agritech.order.OrderItem
import com.uet.agritech.user.User
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface StatisticsOverviewRepository : Repository<OrderItem, Long> {

    @Query(
        """
        SELECT oi
        FROM OrderItem oi
        JOIN FETCH oi.product p
        JOIN FETCH oi.order o
        WHERE p.farmer = :seller
          AND o.status = 'COMPLETED'
          AND o.orderDate >= :start
          AND o.orderDate < :end
        """
    )
    fun findSellerItems(
        @Param("seller") seller: User,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<OrderItem>

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
    fun findBuyerItems(
        @Param("buyer") buyer: User,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): List<OrderItem>
}