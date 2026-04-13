package com.uet.agritech.cart.dto

data class AddToCartRequest(
    val productId: String,
    val quantity: Double
)

data class CartResponse(
    val cartItemId: Long,
    val productId: String,
    val productName: String,
    val price: Double,
    val quantity: Double,
    val thumbnail: String,
    val unit: String,
    val totalPrice: Double,
    val farmerId: String,
    val farmerName: String,
    val farmerPhone: String
)

data class UpdateCartRequest(
    val quantity: Double
)

data class CartMessageResponse(
    val message: String
)