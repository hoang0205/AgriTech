package com.uet.agritech.order

import com.uet.agritech.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {
    fun findAllByUserOrderByOrderDateDesc(user: User): List<Order>
}