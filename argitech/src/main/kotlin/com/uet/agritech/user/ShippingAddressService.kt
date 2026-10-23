package com.uet.agritech.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@Service
class ShippingAddressService(
    private val shippingAddressRepository: ShippingAddressRepository,
    private val userRepository: UserRepository
) {
    fun getMyAddresses(phone: String): List<ShippingAddressResponse> {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        val addresses = shippingAddressRepository.findAllByUser(user)

        return addresses.map { addr ->
            ShippingAddressResponse(
                id = addr.id!!,
                receiverName = addr.receiverName,
                phoneNumber = addr.phoneNumber,
                addressDetail = addr.addressDetail,
                isDefault = addr.isDefault,
                latitude = addr.latitude,
                longitude = addr.longitude
            )
        }
    }

    @Transactional
    fun addAddress(
        phone: String,
        receiverName: String,
        receiverPhone: String,
        detail: String,
        isDefault: Boolean = false,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        require(receiverName.isNotBlank()) {
            "Vui lòng nhập tên người nhận"
        }
        require(receiverPhone.isNotBlank()) {
            "Vui lòng nhập số điện thoại người nhận"
        }
        require(detail.isNotBlank()) {
            "Vui lòng nhập địa chỉ nhận hàng"
        }

        require((latitude == null) == (longitude == null)) {
            "Vị trí phải có đầy đủ vĩ độ và kinh độ"
        }

        if (latitude != null && longitude != null) {
            require(
                latitude.isFinite() &&
                        longitude.isFinite() &&
                        latitude in -90.0..90.0 &&
                        longitude in -180.0..180.0
            ) {
                "Tọa độ địa chỉ không hợp lệ"
            }
        }

        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        val addresses = shippingAddressRepository.findAllByUser(user)
        val isFirst = addresses.isEmpty()

        if (isDefault) {
            addresses.forEach { it.isDefault = false }
            shippingAddressRepository.saveAll(addresses)
        }

        val newAddress = ShippingAddress(
            user = user,
            receiverName = receiverName.trim(),
            phoneNumber = receiverPhone.trim(),
            addressDetail = detail.trim(),
            isDefault = isDefault || isFirst,
            latitude = latitude,
            longitude = longitude
        )

        shippingAddressRepository.save(newAddress)
    }


    @Transactional
    fun updateAddress(
        phone: String,
        addressId: Long,
        receiverName: String,
        receiverPhone: String,
        detail: String,
        isDefault: Boolean,
        latitude: Double?,
        longitude: Double?
    ) {
        require(receiverName.isNotBlank()) { "Vui lòng nhập tên người nhận" }
        require(receiverPhone.isNotBlank()) { "Vui lòng nhập số điện thoại người nhận" }
        require(detail.isNotBlank()) { "Vui lòng nhập địa chỉ nhận hàng" }
        require(latitude != null && longitude != null) { "Vui lòng chọn vị trí trên bản đồ" }
        require(
            latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
        ) { "Tọa độ địa chỉ không hợp lệ" }

        val addresses = addressesOf(phone)
        val address = ownedAddress(addresses, addressId)
        address.receiverName = receiverName.trim()
        address.phoneNumber = receiverPhone.trim()
        address.addressDetail = detail.trim()
        address.latitude = latitude
        address.longitude = longitude

        val defaultId = if (isDefault) addressId else {
            addresses.firstOrNull { it.id != addressId && it.isDefault }?.id
                ?: addresses.firstOrNull { it.id != addressId }?.id
                ?: addressId
        }
        addresses.forEach { it.isDefault = it.id == defaultId }
        shippingAddressRepository.saveAll(addresses)
    }

    @Transactional
    fun deleteAddress(phone: String, addressId: Long) {
        val addresses = addressesOf(phone)
        val address = ownedAddress(addresses, addressId)
        val remaining = addresses.filter { it.id != addressId }.sortedBy { it.id }
        val defaultId = remaining.firstOrNull { it.isDefault }?.id ?: remaining.firstOrNull()?.id
        remaining.forEach { it.isDefault = it.id == defaultId }
        shippingAddressRepository.delete(address)
        shippingAddressRepository.saveAll(remaining)
    }

    private fun addressesOf(phone: String): List<ShippingAddress> {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { RuntimeException("User không tồn tại") }
        return shippingAddressRepository.findAllByUser(user)
    }

    private fun ownedAddress(addresses: List<ShippingAddress>, addressId: Long): ShippingAddress {
        return addresses.find { it.id == addressId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Địa chỉ không tồn tại")
    }

    @Transactional
    fun setDefaultAddress(phone: String, addressId: Long) {
        val addresses = addressesOf(phone)
        ownedAddress(addresses, addressId)

        addresses.forEach { addr ->
            addr.isDefault = (addr.id == addressId)
        }
        shippingAddressRepository.saveAll(addresses)
    }
}
