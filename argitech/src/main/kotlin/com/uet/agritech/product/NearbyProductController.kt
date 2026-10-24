package com.uet.agritech.product

import com.uet.agritech.product.dto.ProductResponse
import org.springframework.data.domain.Page
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products/nearby")
class NearbyProductController(private val service: NearbyProductService) {
    @GetMapping
    fun getProducts(
        @RequestParam addressId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) keyword: String?
    ): Page<ProductResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name.orEmpty()
        return service.getProducts(phone, addressId, page, size, category, keyword)
    }
}
