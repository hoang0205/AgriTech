package com.uet.agritech.user

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/user/pickup-location")
class SellerPickupController(
    private val sellerPickupService: SellerPickupService
) {
    @GetMapping
    fun getPickupLocation(
        principal: Principal
    ): SellerPickupResponse {
        return sellerPickupService.getPickupLocation(principal.name)
    }

    @PutMapping
    fun updatePickupLocation(
        principal: Principal,
        @RequestBody request: UpdateSellerPickupRequest
    ): SellerPickupResponse {
        return sellerPickupService.updatePickupLocation(
            phone = principal.name,
            request = request
        )
    }
}