package com.uet.agritech.review

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReviewRepository : JpaRepository<Review, String> {
    fun findByProductId(productId: String, pageable: Pageable): Page<Review>
    fun findByUserId(userId: String, pageable: Pageable): Page<Review>
}