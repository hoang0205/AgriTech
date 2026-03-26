package com.uet.agritech.product.dto

data class ProductRequest(
    val name: String,
    val category: String,
    val price: Double,
    val quantity: Double,
    val unit: String,
    val description: String,
    val imageUrl: String? = null
)

data class ProductResponse(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val quantity: Double,
    val unit: String,
    val description: String,
    val imageUrl: String?,
    val farmerName: String
)