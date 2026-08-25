package com.uet.agritech.review.dto

import java.time.LocalDateTime

data class ReviewRequestDto(
    val productId: String,
    val rating: Int,
    val comment: String?,
    val imageUrls: List<String> = emptyList()
)

data class ReviewResponseDto(
    val id: String,
    val userId: String,
    val userName: String,
    val rating: Int,
    val comment: String?,
    val imageUrls: List<String>,
    val createdAt: LocalDateTime
)

data class ReviewSummaryDto(
    val totalReviews: Int,
    val averageRating: Double,
    val star5: Int,
    val star4: Int,
    val star3: Int,
    val star2: Int,
    val star1: Int
)