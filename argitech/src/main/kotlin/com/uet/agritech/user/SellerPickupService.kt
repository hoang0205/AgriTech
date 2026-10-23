package com.uet.agritech.user

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class UpdateSellerPickupRequest(
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class SellerPickupResponse(
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val configured: Boolean
)

@Service
class SellerPickupService(
    private val userRepository: UserRepository,
    private val entityManager: EntityManager
) {
    @Transactional(readOnly = true)
    fun getPickupLocation(phone: String): SellerPickupResponse {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tài khoản không tồn tại"
                )
            }

        requireFarmer(user)
        return toResponse(user)
    }

    @Transactional
    fun updatePickupLocation(
        phone: String,
        request: UpdateSellerPickupRequest
    ): SellerPickupResponse {
        val address = request.address?.trim()
        val latitude = request.latitude
        val longitude = request.longitude

        require(!address.isNullOrBlank()) {
            "Vui lòng nhập địa chỉ lấy hàng"
        }
        require(address.length <= 1000) {
            "Địa chỉ lấy hàng không được vượt quá 1000 ký tự"
        }
        require(latitude != null && longitude != null) {
            "Vui lòng chọn vị trí lấy hàng trên bản đồ"
        }
        require(
            latitude.isFinite() &&
                    longitude.isFinite() &&
                    latitude in -90.0..90.0 &&
                    longitude in -180.0..180.0
        ) {
            "Tọa độ lấy hàng không hợp lệ"
        }

        val user = userRepository.findByPhoneForAuthUpdate(phone)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tài khoản không tồn tại"
                )
            }

        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE)
        requireFarmer(user)

        user.pickupAddress = address
        user.pickupLatitude = latitude
        user.pickupLongitude = longitude

        userRepository.save(user)
        return toResponse(user)
    }

    private fun requireFarmer(user: User) {
        if (user.role != Role.FARMER) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Chỉ người bán được quản lý địa điểm lấy hàng"
            )
        }
    }

    private fun toResponse(user: User): SellerPickupResponse {
        return SellerPickupResponse(
            address = user.pickupAddress,
            latitude = user.pickupLatitude,
            longitude = user.pickupLongitude,
            configured = !user.pickupAddress.isNullOrBlank() &&
                    user.pickupLatitude != null &&
                    user.pickupLongitude != null
        )
    }
}