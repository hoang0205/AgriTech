package com.uet.agritech.order

import com.uet.agritech.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderItemRepository : JpaRepository<OrderItem, Long> {
    fun findAllByOrder(order: Order): List<OrderItem>

    fun findAllByProductFarmer(farmer: User): List<OrderItem>
}