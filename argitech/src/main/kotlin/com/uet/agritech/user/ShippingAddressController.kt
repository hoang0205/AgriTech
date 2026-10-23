package com.uet.agritech.user

import com.uet.agritech.user.dto.AddressRequest
import com.uet.agritech.user.dto.MessageResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user/addresses")
class ShippingAddressController(private val service: ShippingAddressService) {

    @GetMapping
    fun getMyAddresses(): List<ShippingAddressResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        return service.getMyAddresses(phone.toString())
    }

    @PostMapping
    fun addAddress(@RequestBody request: AddressRequest) : MessageResponse {
        val phone = SecurityContextHolder.getContext().authentication?.name
        service.addAddress(
            phone = phone.toString(),
            receiverName = request.receiverName,
            receiverPhone = request.receiverPhone,
            detail = request.detail,
            isDefault = request.isDefault,
            latitude = request.latitude,
            longitude = request.longitude
        )
        return MessageResponse("Thêm địa chỉ thành công")
    }


    @PutMapping("/{id}")
    fun updateAddress(@PathVariable id: Long, @RequestBody request: AddressRequest): MessageResponse {
        val phone = SecurityContextHolder.getContext().authentication?.name
        service.updateAddress(
            phone = phone.toString(),
            addressId = id,
            receiverName = request.receiverName,
            receiverPhone = request.receiverPhone,
            detail = request.detail,
            isDefault = request.isDefault,
            latitude = request.latitude,
            longitude = request.longitude
        )
        return MessageResponse("Cập nhật địa chỉ thành công")
    }

    @DeleteMapping("/{id}")
    fun deleteAddress(@PathVariable id: Long): MessageResponse {
        val phone = SecurityContextHolder.getContext().authentication?.name
        service.deleteAddress(phone.toString(), id)
        return MessageResponse("Đã xóa địa chỉ")
    }

    @PatchMapping("/{id}/default")
    fun setDefault(@PathVariable id: Long) : MessageResponse {
        val phone = SecurityContextHolder.getContext().authentication?.name
        service.setDefaultAddress(phone.toString(), id)
        return MessageResponse("Đã đặt địa chỉ này làm mặc định")
    }
}

