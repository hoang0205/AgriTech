package com.uet.agritech.cart

import com.uet.agritech.product.Product
import com.uet.agritech.user.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CartItemRepository : JpaRepository<CartItem, Long> {
    fun findByUserAndProduct(user: User, product: Product): CartItem?

    fun findAllByUser(user: User): List<CartItem>

    fun deleteAllByUser(user: User)

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartItem c where c.id in :ids order by c.id")
    fun findAllForCheckout(
        @Param("ids") ids: List<Long>
    ): List<CartItem>
}