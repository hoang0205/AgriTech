package com.uet.agritech.review.dto

data class ReviewRequestDto(
    val productId: String,
    val rating: Int,
    val comment: String?,
    val imageUrls: List<String> = emptyList()
)