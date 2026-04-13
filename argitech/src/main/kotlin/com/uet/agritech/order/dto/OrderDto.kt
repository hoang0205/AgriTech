package com.uet.agritech.order.dto

data class CheckoutRequest(
    val shippingAddress: String,
    val phoneNumber: String,
    val selectedCartItemIds: List<Long>
)

data class FarmerOrderResponse(
    val orderId: Long,
    val orderDate: String,
    val buyerName: String,
    val buyerPhone: String,
    val buyerId: String,
    val shippingAddress: String,
    val status: String,
    val items: List<FarmerOrderItemDTO>,
    val totalRevenueFromThisOrder: Double
)

data class FarmerOrderItemDTO(
    val productName: String,
    val quantity: Double,
    val unit: String,
    val price: Double,
    val thumbnail: String
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

data class BuyerOrderResponse(
    val orderId: Long,
    val orderDate: String,
    val status: String,
    val shippingAddress: String,
    val totalAmount: Double,
    val items: List<BuyerOrderItemDTO>
)

data class BuyerOrderItemDTO(
    val productId: String,
    val productName: String,
    val farmerName: String,
    val quantity: Double,
    val unit: String,
    val price: Double,
    val thumbnail: String
)