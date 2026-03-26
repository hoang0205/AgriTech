package com.uet.agritech.product

import com.uet.agritech.product.dto.ProductRequest
import com.uet.agritech.product.dto.ProductResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productService: ProductService
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
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Any> {
        return ResponseEntity.ok(productService.searchProducts(keyword, page, size))
    }

    @GetMapping("/suggest")
    fun suggestProducts(@RequestParam keyword: String): ResponseEntity<List<String>> {
        return ResponseEntity.ok(productService.suggestProductNames(keyword))
    }

    @GetMapping("/{id}")
    fun getProductById(@PathVariable id: String): ResponseEntity<ProductResponse> {
        return ResponseEntity.ok(productService.getProductById(id))
    }
}