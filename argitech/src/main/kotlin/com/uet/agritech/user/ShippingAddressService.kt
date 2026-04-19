package com.uet.agritech.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
                isDefault = addr.isDefault
            )
        }
    }

    @Transactional
    fun addAddress(phone: String, receiverName: String, receiverPhone: String, detail: String, isDefault: Boolean = false) {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        val isFirst = shippingAddressRepository.findAllByUser(user).isEmpty()

        if (isDefault) {
            val addresses = shippingAddressRepository.findAllByUser(user)
            addresses.forEach { it.isDefault = false }
            shippingAddressRepository.saveAll(addresses)
        }

        val newAddress = ShippingAddress(
            user = user,
            receiverName = receiverName,
            phoneNumber = receiverPhone,
            addressDetail = detail,
            isDefault = isDefault || isFirst
        )
        shippingAddressRepository.save(newAddress)
    }


    @Transactional
    fun setDefaultAddress(phone: String, addressId: Long) {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        val addresses = shippingAddressRepository.findAllByUser(user)

        addresses.forEach { addr ->
            addr.isDefault = (addr.id == addressId)
        }
        shippingAddressRepository.saveAll(addresses)
    }
}