package com.uet.agritech.delivery

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/delivery")
class DeliveryQuoteController(
    private val deliveryQuoteService: DeliveryQuoteService
) {
    @PostMapping("/quote")
    fun quote(
        principal: Principal,
        @RequestBody request: DeliveryQuoteRequest
    ): DeliveryQuoteResponse {
        return deliveryQuoteService.quote(
            phone = principal.name,
            request = request
        )
    }
}