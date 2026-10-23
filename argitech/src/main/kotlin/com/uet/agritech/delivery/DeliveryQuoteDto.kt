package com.uet.agritech.delivery

data class DeliveryQuoteRequest(
    val addressId: Long,
    val selectedCartItemIds: List<Long>
)

enum class DeliveryStatus {
    AVAILABLE,
    OUT_OF_RANGE,
    SELLER_ADDRESS_REQUIRED,
    RECEIVER_ADDRESS_REQUIRED
}

data class SellerDeliveryQuote(
    val sellerId: String,
    val sellerName: String,
    val cartItemIds: List<Long>,
    val status: DeliveryStatus,
    val message: String,
    val distanceKm: Double? = null,
    val shippingFee: Long? = null,
    val pickupArea: String? = null
)

data class DeliveryQuoteResponse(
    val addressId: Long,
    val maxDistanceKm: Double,
    val sellers: List<SellerDeliveryQuote>,
    val allDeliverable: Boolean,
    val totalShippingFee: Long?,
    val distanceType: String = "STRAIGHT_LINE"
)