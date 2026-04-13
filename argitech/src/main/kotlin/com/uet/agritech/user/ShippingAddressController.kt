package com.uet.agritech.user

import com.uet.agritech.user.dto.AddressRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user/addresses")
class ShippingAddressController(private val service: ShippingAddressService) {

    @GetMapping
    fun getMyAddresses(): List<ShippingAddress> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        return service.getMyAddresses(phone.toString())
    }

    @PostMapping
    fun addAddress(@RequestBody request: AddressRequest) {
        val phone = SecurityContextHolder.getContext().authentication?.name
        service.addAddress(phone.toString(), request.receiverName, request.receiverPhone, request.detail)
    }

    @PatchMapping("/{id}/default")
    fun setDefault(@PathVariable id: Long) {
        val phone = SecurityContextHolder.getContext().authentication?.name
        service.setDefaultAddress(phone.toString(), id)
    }
}

