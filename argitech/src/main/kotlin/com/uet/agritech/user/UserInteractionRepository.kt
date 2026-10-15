package com.uet.agritech.user

import com.uet.agritech.product.Product
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserInteractionRepository : JpaRepository<UserInteraction, Long> {

    @Query("""
        SELECT i.product
        FROM UserInteraction i
        WHERE i.user = :user
          AND i.product.quantity > 0
          AND i.actionType IN ('VIEW', 'ADD_TO_CART', 'PURCHASE')
        GROUP BY i.product
        ORDER BY SUM(i.weight) DESC, i.product.id ASC
    """)
    fun findTopProductsByUser(
        user: User,
        pageable: Pageable
    ): List<Product>

    @Query("""
        SELECT i.product.category, SUM(i.weight)
        FROM UserInteraction i
        WHERE i.user = :user
          AND i.actionType IN ('VIEW', 'ADD_TO_CART', 'PURCHASE')
        GROUP BY i.product.category
        ORDER BY SUM(i.weight) DESC, i.product.category ASC
    """)
    fun findFavoriteCategoriesByUser(
        user: User,
        pageable: Pageable
    ): List<Array<Any>>

    @Query("""
        SELECT DISTINCT i.product.id
        FROM UserInteraction i
        WHERE i.user = :user
          AND i.actionType IN ('VIEW', 'ADD_TO_CART', 'PURCHASE')
    """)
    fun findViewedProductIdsByUser(user: User): List<String>

    @Query("""
        SELECT i.user
        FROM UserInteraction i
        WHERE i.user <> :user
          AND i.actionType IN ('VIEW', 'ADD_TO_CART', 'PURCHASE')
          AND i.product.category IN (
              SELECT DISTINCT own.product.category
              FROM UserInteraction own
              WHERE own.user = :user
                AND own.actionType IN ('VIEW', 'ADD_TO_CART', 'PURCHASE')
          )
        GROUP BY i.user
        ORDER BY SUM(i.weight) DESC, i.user.id ASC
    """)
    fun findSimilarUsersByCategories(
        user: User,
        pageable: Pageable
    ): List<User>

    @Query("""
        SELECT i.product
        FROM UserInteraction i
        WHERE i.user = :similarUser
          AND i.product.quantity > 0
          AND i.actionType IN ('VIEW', 'ADD_TO_CART', 'PURCHASE')
          AND NOT EXISTS (
              SELECT own.id
              FROM UserInteraction own
              WHERE own.user = :currentUser
                AND own.product = i.product
                AND own.actionType IN ('VIEW', 'ADD_TO_CART', 'PURCHASE')
          )
        GROUP BY i.product
        ORDER BY SUM(i.weight) DESC, i.product.id ASC
    """)
    fun findUnseenProductsFromSimilarUser(
        similarUser: User,
        currentUser: User,
        pageable: Pageable
    ): List<Product>
}