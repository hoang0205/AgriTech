package com.uet.agritech.review

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ReviewRepository : JpaRepository<Review, String> {
    fun findByProductId(productId: String, pageable: Pageable): Page<Review>
    fun findByUserId(userId: String, pageable: Pageable): Page<Review>

    fun countByProductId(productId: String): Long

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId")
    fun getAverageRatingByProductId(productId: String): Double?

    fun countByProductIdAndRating(productId: String, rating: Int): Long

    fun existsByProductIdAndUserId(productId: String, userId: String): Boolean
}