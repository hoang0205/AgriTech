package com.uet.agritech.product

import com.uet.agritech.delivery.DeliveryQuoteService
import com.uet.agritech.delivery.DeliveryStatus
import com.uet.agritech.product.dto.ProductResponse
import com.uet.agritech.user.ShippingAddressRepository
import com.uet.agritech.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class NearbyProductService(
    private val products: ProductRepository,
    private val addresses: ShippingAddressRepository,
    private val users: UserRepository,
    private val delivery: DeliveryQuoteService
) {
    @Transactional(readOnly = true)
    fun getProducts(
        phone: String,
        addressId: Long,
        page: Int,
        size: Int,
        category: String?,
        keyword: String?
    ): Page<ProductResponse> {
        require(page >= 0 && size in 1..100) { "Trang hoặc số lượng sản phẩm không hợp lệ" }
        require(addressId > 0) { "Địa chỉ nhận hàng không hợp lệ" }
        val user = users.findByPhoneNumber(phone).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập")
        }
        val address = addresses.findById(addressId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Địa chỉ nhận hàng không còn tồn tại")
        }
        if (address.user.id != user.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn chỉ được dùng địa chỉ của mình")
        }
        require(address.addressDetail.isNotBlank() && delivery.validCoordinates(address.latitude, address.longitude)) {
            "Vui lòng bổ sung vị trí nhận hàng trên bản đồ"
        }
        val normalizedKeyword = keyword?.trim()?.takeIf { it.isNotEmpty() }
        require((normalizedKeyword?.length ?: 0) <= 255) { "Từ khóa không được vượt quá 255 ký tự" }
        val pattern = normalizedKeyword?.let {
            "%${it.replace("!", "!!").replace("%", "!%").replace("_", "!_")}%"
        }
        return products.findProductsByDistance(
            buyerId = requireNotNull(user.id),
            latitude = requireNotNull(address.latitude),
            longitude = requireNotNull(address.longitude),
            category = category?.trim()?.takeIf { it.isNotEmpty() },
            pattern = pattern,
            pageable = PageRequest.of(page, size)
        ).map { product ->
            val seller = product.farmer
            val configured = !seller.pickupAddress.isNullOrBlank() &&
                delivery.validCoordinates(seller.pickupLatitude, seller.pickupLongitude)
            val distance = if (configured) delivery.distanceKm(
                requireNotNull(seller.pickupLatitude), requireNotNull(seller.pickupLongitude),
                requireNotNull(address.latitude), requireNotNull(address.longitude)
            ) else null
            val status = when {
                distance == null -> DeliveryStatus.SELLER_ADDRESS_REQUIRED
                distance > DeliveryQuoteService.MAX_DISTANCE_KM -> DeliveryStatus.OUT_OF_RANGE
                else -> DeliveryStatus.AVAILABLE
            }
            ProductResponse(
                id = requireNotNull(product.id), name = product.name, category = product.category,
                price = product.price, quantity = product.quantity, unit = product.unit,
                description = product.description, imageUrls = product.imageUrls,
                farmerId = requireNotNull(seller.id), farmerName = seller.fullName,
                farmerAvatar = seller.avatarUrl, farmerPhone = seller.phoneNumber,
                rating = product.rating, reviewCount = product.reviewCount,
                distanceKm = distance, deliveryStatus = status
            )
        }
    }
}
