package com.uet.agritech.user

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserInteractionRepository : JpaRepository<UserInteraction, Long> {

    @Query("""
        SELECT i.product, SUM(i.weight) as totalScore 
        FROM UserInteraction i 
        WHERE i.user = :user 
        GROUP BY i.product 
        ORDER BY totalScore DESC
    """)
    fun findTopProductsByUser(user: User, pageable: Pageable): List<Array<Any>>

    // ← THÊM CÁC HÀM NÀY

    @Query("""
        SELECT DISTINCT i.product.category, SUM(i.weight) as totalWeight
        FROM UserInteraction i 
        WHERE i.user = :user 
        GROUP BY i.product.category 
        ORDER BY totalWeight DESC
    """)
    fun findFavoriteCategoriesByUser(user: User, pageable: Pageable): List<Array<Any>>

    @Query("""
        SELECT DISTINCT i.product.id
        FROM UserInteraction i 
        WHERE i.user = :user
    """)
    fun findViewedProductIdsByUser(user: User): List<String>

    @Query("""
        SELECT i.user
        FROM UserInteraction i 
        WHERE i.product.category IN (
            SELECT DISTINCT i2.product.category
            FROM UserInteraction i2 
            WHERE i2.user = :user
        )
        AND i.user != :user
        GROUP BY i.user
        ORDER BY COUNT(i) DESC
    """)
    fun findSimilarUsersByCategories(user: User, pageable: Pageable): List<Array<Any>>
}
