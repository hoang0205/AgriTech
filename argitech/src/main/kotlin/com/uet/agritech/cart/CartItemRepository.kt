package com.uet.agritech.cart

import com.uet.agritech.product.Product
import com.uet.agritech.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CartItemRepository : JpaRepository<CartItem, Long> {
    fun findByUserAndProduct(user: User, product: Product): CartItem?

    fun findAllByUser(user: User): List<CartItem>

    fun deleteAllByUser(user: User)
}