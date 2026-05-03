package com.uet.agritech.order

import com.uet.agritech.order.dto.OrderStatusCount
import com.uet.agritech.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findAllByUserOrderByOrderDateDesc(user: User): List<Order>

    fun findAllByUserOrderByIdDesc(user: User): List<Order>

    fun findAllByUserAndStatusOrderByOrderDateDesc(user: User, status: String): List<Order>

    @Query("SELECT o.status AS status, COUNT(o) AS count FROM Order o WHERE o.user = :user GROUP BY o.status")
    fun countOrdersByStatusForUser(user: User): List<OrderStatusCount>
}