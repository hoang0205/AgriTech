package com.uet.agritech.order.dto

data class CheckoutRequest(
    val shippingAddress: String,
    val phoneNumber: String,
    val selectedCartItemIds: List<Long>
)

data class FarmerOrderResponse(
    val orderId: Long,
    val orderDate: String,
    val buyerPhone: String,
    val shippingAddress: String,
    val status: String,
    val items: List<FarmerOrderItemDTO>,
    val totalRevenueFromThisOrder: Double
)

data class FarmerOrderItemDTO(
    val productName: String,
    val quantity: Double,
    val price: Double
)

data class UpdateOrderStatusRequest(
    val status: OrderStatus
)

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPING,
    COMPLETED,
    CANCELLED
}

data class OrderMessageResponse(
    val message: String
)