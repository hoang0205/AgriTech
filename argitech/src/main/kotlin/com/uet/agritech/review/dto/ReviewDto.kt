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