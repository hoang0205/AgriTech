package com.uet.agritech.product

import com.uet.agritech.product.dto.ProductResponse
import com.uet.agritech.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/products")
class MyProductsController(
    private val userRepository: UserRepository,
    private val productService: ProductService,
    private val stockService: ProductStockService,
    private val editService: MyProductEditService
) {

    @GetMapping("/mine/{productId}")
    fun getEditableProduct(@PathVariable productId: String): EditableProductResponse {
        return editService.getProduct(currentPhone(), productId)
    }

    @PutMapping("/mine/{productId}")
    fun updateProduct(
        @PathVariable productId: String,
        @RequestBody request: UpdateMyProductRequest
    ): EditableProductResponse {
        return editService.updateProduct(currentPhone(), productId, request)
    }

    private fun currentPhone(): String {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication.name == "anonymousUser") {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập")
        }
        return authentication.name
    }

    @PatchMapping("/{productId}/stock")
    fun updateStock(
        @PathVariable productId: String,
        @RequestBody request: UpdateProductStockRequest
    ): ProductStockResponse {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication.name == "anonymousUser") {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập")
        }
        return stockService.updateStock(authentication.name, productId, request)
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    fun getMyProducts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "12") size: Int
    ): Page<ProductResponse> {
        val authentication = SecurityContextHolder
            .getContext()
            .authentication

        if (
            authentication == null ||
            !authentication.isAuthenticated ||
            authentication.name == "anonymousUser"
        ) {
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Bạn cần đăng nhập"
            )
        }

        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Thông tin phân trang không hợp lệ"
            )
        }

        val user = userRepository
            .findByPhoneNumber(authentication.name)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tài khoản không tồn tại"
                )
            }

        return productService.getProductsByFarmerId(
            farmerId = requireNotNull(user.id),
            page = page,
            size = size
        )
    }
}
