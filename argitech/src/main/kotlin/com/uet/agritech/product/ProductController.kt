package com.uet.agritech.product

import com.uet.agritech.product.dto.ProductCostResponse
import com.uet.agritech.product.dto.ProductRequest
import com.uet.agritech.product.dto.ProductResponse
import com.uet.agritech.product.dto.UpdateProductCostRequest
import com.uet.agritech.user.RecommendationService
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productService: ProductService,
    private val recommendationService: RecommendationService
) {

    @PostMapping
    fun createProduct(@RequestBody request: ProductRequest): ResponseEntity<ProductResponse> {
        return ResponseEntity.ok(productService.createProduct(request))
    }

    @GetMapping
    fun getAllProducts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(productService.getAllProducts(page, size))
    }

    @GetMapping("/random")
    fun getRandomProducts(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<ProductResponse>> {
        return ResponseEntity.ok(productService.getRandomProducts(limit))
    }

    @GetMapping("/category/{category}")
    fun getProductsByCategory(
        @PathVariable category: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(productService.getProductsByCategory(category, page, size))
    }

    @GetMapping("/search")
    fun searchProducts(
        @RequestParam("keyword") keyword: String,
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "10") size: Int,
        @RequestParam(name = "farmerId", required = false) farmerId: String?
    ): ResponseEntity<Page<ProductResponse>> {
        return ResponseEntity.ok(
            productService.searchProducts(
                keyword = keyword,
                page = page,
                size = size,
                farmerId = farmerId
            )
        )
    }

    @GetMapping("/suggest")
    fun suggestProducts(
        @RequestParam("keyword") keyword: String,
        @RequestParam(name = "farmerId", required = false) farmerId: String?
    ): ResponseEntity<List<String>> {
        return ResponseEntity.ok(
            productService.suggestProductNames(
                keyword = keyword,
                farmerId = farmerId
            )
        )
    }

    @GetMapping("/{id}")
    fun getProductById(@PathVariable id: String): ResponseEntity<ProductResponse> {
        return ResponseEntity.ok(productService.getProductById(id))
    }

    @GetMapping("/recommendations")
    fun getRecommendations(
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<ProductResponse>> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        val recommendations = recommendationService.getMixedRecommendations(
            phone.toString(),
            limit.coerceIn(1, 50)
        )
        return ResponseEntity.ok(recommendations)
    }

    @GetMapping("/farmer/{farmerId}")
    fun getProductsByFarmer(
        @PathVariable farmerId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<ProductResponse>> {
        val products = productService.getProductsByFarmerId(farmerId, page, size)
        return ResponseEntity.ok(products)
    }
}

@RestController
@RequestMapping("/api/products")
class ProductCostController(
    private val productCostService: ProductCostService
) {

    @GetMapping("/my-costs")
    fun getMyProductCosts(): ResponseEntity<List<ProductCostResponse>> {
        val products = productCostService.getMyProductCosts(
            currentPhone()
        )

        return ResponseEntity.ok(products)
    }

    @PutMapping("/{productId}/cost")
    fun updateCost(
        @PathVariable productId: String,
        @RequestBody request: UpdateProductCostRequest
    ): ResponseEntity<ProductCostResponse> {
        val product = productCostService.updateCost(
            sellerPhone = currentPhone(),
            productId = productId,
            request = request
        )

        return ResponseEntity.ok(product)
    }

    private fun currentPhone(): String {
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

        return authentication.name
    }
}