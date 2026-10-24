package com.uet.agritech.delivery

import com.uet.agritech.cart.CartItemRepository
import com.uet.agritech.user.ShippingAddressRepository
import com.uet.agritech.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class DeliveryQuoteService(
    private val userRepository: UserRepository,
    private val addressRepository: ShippingAddressRepository,
    private val cartItemRepository: CartItemRepository
) {
    companion object {
        const val MAX_DISTANCE_KM = 30.0
        private const val EARTH_RADIUS_KM = 6371.0
    }

    @Transactional(readOnly = true)
    fun quote(
        phone: String,
        request: DeliveryQuoteRequest
    ): DeliveryQuoteResponse {
        require(request.addressId > 0) {
            "Địa chỉ nhận hàng không hợp lệ"
        }

        require(
            request.selectedCartItemIds.isNotEmpty() &&
                    request.selectedCartItemIds.all { it > 0 }
        ) {
            "Vui lòng chọn sản phẩm trong giỏ hàng"
        }

        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tài khoản không tồn tại"
                )
            }

        val address = addressRepository.findById(request.addressId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy địa chỉ nhận hàng"
                )
            }

        if (address.user.id != user.id) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Bạn chỉ được dùng địa chỉ nhận hàng của mình"
            )
        }

        val selectedIds = request.selectedCartItemIds.distinct()
        val cartItems = cartItemRepository
            .findAllById(selectedIds)
            .sortedBy { it.id }

        require(
            cartItems.size == selectedIds.size &&
                    cartItems.all { it.user.id == user.id }
        ) {
            "Giỏ hàng đã thay đổi hoặc không hợp lệ. Vui lòng tải lại."
        }

        val quotes = cartItems.groupBy { requireNotNull(it.product.farmer.id) }
            .map { (_, items) -> quoteSeller(address, items.first().product.farmer,
                items.map { requireNotNull(it.id) }) }

        val allDeliverable = quotes.all {
            it.status == DeliveryStatus.AVAILABLE
        }

        return DeliveryQuoteResponse(
            addressId = request.addressId,
            maxDistanceKm = MAX_DISTANCE_KM,
            sellers = quotes,
            allDeliverable = allDeliverable,
            totalShippingFee = if (allDeliverable) {
                quotes.sumOf { requireNotNull(it.shippingFee) }
            } else {
                null
            }
        )
    }

    fun quoteSeller(address: com.uet.agritech.user.ShippingAddress,
                    seller: com.uet.agritech.user.User,
                    cartItemIds: List<Long> = emptyList()): SellerDeliveryQuote {
        val sellerId = requireNotNull(seller.id)
        val pickupArea = pickupAreaOf(seller.pickupAddress)
        val receiverConfigured = address.addressDetail.isNotBlank() &&
            validCoordinates(address.latitude, address.longitude)
        if (!receiverConfigured) {
            return SellerDeliveryQuote(
                sellerId = sellerId,
                sellerName = seller.fullName,
                pickupArea = pickupArea,
                cartItemIds = cartItemIds,
                status = DeliveryStatus.RECEIVER_ADDRESS_REQUIRED,
                message = "Vui lòng cập nhật địa chỉ nhận hàng và chọn vị trí trên bản đồ."
            )
        }

        val sellerConfigured =
            !seller.pickupAddress.isNullOrBlank() &&
                    validCoordinates(
                        seller.pickupLatitude,
                        seller.pickupLongitude
                    )

        if (!sellerConfigured) {
            return SellerDeliveryQuote(
                sellerId = sellerId,
                sellerName = seller.fullName,
                pickupArea = pickupArea,
                cartItemIds = cartItemIds,
                status = DeliveryStatus.SELLER_ADDRESS_REQUIRED,
                message = "Người bán chưa cập nhật đầy đủ địa điểm lấy hàng."
            )
        }

        val distance = distanceKm(
            fromLatitude = requireNotNull(seller.pickupLatitude),
            fromLongitude = requireNotNull(seller.pickupLongitude),
            toLatitude = requireNotNull(address.latitude),
            toLongitude = requireNotNull(address.longitude)
        )

        val fee = feeForDistance(distance)

        return if (fee == null) {
            SellerDeliveryQuote(
                sellerId = sellerId,
                sellerName = seller.fullName,
                pickupArea = pickupArea,
                cartItemIds = cartItemIds,
                status = DeliveryStatus.OUT_OF_RANGE,
                message = "Ngoài phạm vi giao hàng 30 km của người bán.",
                distanceKm = distance
            )
        } else {
            SellerDeliveryQuote(
                sellerId = sellerId,
                sellerName = seller.fullName,
                pickupArea = pickupArea,
                cartItemIds = cartItemIds,
                status = DeliveryStatus.AVAILABLE,
                message = "Trong phạm vi giao hàng.",
                distanceKm = distance,
                shippingFee = fee
            )
        }
    }

    private fun pickupAreaOf(address: String?): String? {
        val parts = address.orEmpty()
            .replace(
                Regex(
                    """,?\s*(Việt Nam|Vietnam)\s*$""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (parts.size >= 3) {
            parts.takeLast(2).joinToString(", ")
        } else {
            null
        }
    }

    fun validCoordinates(
        latitude: Double?,
        longitude: Double?
    ): Boolean {
        return latitude != null &&
                longitude != null &&
                latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
    }

    private fun feeForDistance(distance: Double): Long? {
        return when {
            distance <= 5.0 -> 15_000L
            distance <= 15.0 -> 25_000L
            distance <= MAX_DISTANCE_KM -> 40_000L
            else -> null
        }
    }

    fun distanceKm(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): Double {
        val latitudeDifference = Math.toRadians(
            toLatitude - fromLatitude
        )
        val longitudeDifference = Math.toRadians(
            toLongitude - fromLongitude
        )

        val fromLatitudeRadians = Math.toRadians(fromLatitude)
        val toLatitudeRadians = Math.toRadians(toLatitude)

        val sinLatitude = sin(latitudeDifference / 2.0)
        val sinLongitude = sin(longitudeDifference / 2.0)

        val a = (
                sinLatitude * sinLatitude +
                        cos(fromLatitudeRadians) *
                        cos(toLatitudeRadians) *
                        sinLongitude * sinLongitude
                ).coerceIn(0.0, 1.0)

        return EARTH_RADIUS_KM * 2.0 *
                atan2(sqrt(a), sqrt(1.0 - a))
    }
}
