package com.uet.agritech.product.dto

data class ProductRequest(
    val name: String,
    val category: String,
    val price: Double,
    val quantity: Double,
    val unit: String,
    val description: String,
    val imageUrls: MutableList<String>,
    val costPrice: Double? = null
)

data class ProductResponse(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val quantity: Double,
    val unit: String,
    val description: String,
    val imageUrls: MutableList<String>,
    val farmerId: String,
    val farmerAvatar: String?,
    val farmerPhone: String?,
    val farmerName: String,
    val rating: Double,
    val reviewCount: Int,
    val aiPros: String? = null,
    val aiCons: String? = null
)

data class UpdateProductCostRequest(
    val costPrice: Double? = null
)

data class ProductCostResponse(
    val productId: String,
    val productName: String,
    val unit: String,
    val sellingPrice: Double,
    val costPrice: Double?,
    val quantity: Double
)