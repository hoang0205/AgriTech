package com.uet.agritech.product

import com.uet.agritech.product.dto.ProductRequest
import com.uet.agritech.product.dto.ProductResponse
import com.uet.agritech.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) {

    fun createProduct(request: ProductRequest): ProductResponse {
        val authentication = SecurityContextHolder.getContext().authentication
        val phoneNumber = authentication?.name

        val farmer = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { RuntimeException("Không tìm thấy thông tin người dùng!") }

        val product = Product(
            name = request.name,
            category = request.category,
            price = request.price,
            quantity = request.quantity,
            unit = request.unit,
            description = request.description,
            imageUrl = request.imageUrl,
            farmer = farmer
        )

        val savedProduct = productRepository.save(product)

        return ProductResponse(
            id = savedProduct.id!!,
            name = savedProduct.name,
            category = savedProduct.category,
            price = savedProduct.price,
            quantity = savedProduct.quantity,
            unit = savedProduct.unit,
            description = savedProduct.description,
            imageUrl = savedProduct.imageUrl,
            farmerName = farmer.fullName
        )
    }

    fun getAllProducts(page: Int, size: Int): Page<ProductResponse> {
        val pageable = PageRequest.of(page, size, Sort.by("id").descending())

        val productPage = productRepository.findAll(pageable)

        return productPage.map { product ->
            ProductResponse(
                id = product.id!!,
                name = product.name,
                category = product.category,
                price = product.price,
                quantity = product.quantity,
                unit = product.unit,
                description = product.description,
                imageUrl = product.imageUrl,
                farmerName = product.farmer.fullName
            )
        }
    }

    fun getRandomProducts(limit: Int): List<ProductResponse> {
        val randomProducts = productRepository.findRandomProducts(limit)

        return randomProducts.map { product ->
            ProductResponse(
                id = product.id!!,
                name = product.name,
                category = product.category,
                price = product.price,
                quantity = product.quantity,
                unit = product.unit,
                description = product.description,
                imageUrl = product.imageUrl,
                farmerName = product.farmer.fullName
            )
        }
    }

    fun getProductsByCategory(category: String, page: Int, size: Int): Page<ProductResponse> {
        val pageable = PageRequest.of(page, size, Sort.by("id").descending())

        val productPage = productRepository.findByCategory(category, pageable)

        return productPage.map { product ->
            ProductResponse(
                id = product.id!!,
                name = product.name,
                category = product.category,
                price = product.price,
                quantity = product.quantity,
                unit = product.unit,
                description = product.description,
                imageUrl = product.imageUrl,
                farmerName = product.farmer.fullName
            )
        }
    }

    fun searchProducts(keyword: String, page: Int, size: Int): Page<ProductResponse> {
        val pageable = PageRequest.of(page, size, Sort.by("id").descending())

        val productPage = productRepository.findByNameContainingIgnoreCase(keyword, pageable)

        return productPage.map { product ->
            ProductResponse(
                id = product.id!!,
                name = product.name,
                category = product.category,
                price = product.price,
                quantity = product.quantity,
                unit = product.unit,
                description = product.description,
                imageUrl = product.imageUrl,
                farmerName = product.farmer.fullName
            )
        }
    }

    fun suggestProductNames(keyword: String): List<String> {
        return productRepository.suggestProductNames(keyword)
    }

    fun getProductById(id: String): ProductResponse {
        val product = productRepository.findById(id)
            .orElseThrow { RuntimeException("Không tìm thấy sản phẩm này hoặc đã bị xóa!") }

        return ProductResponse(
            id = product.id!!,
            name = product.name,
            category = product.category,
            price = product.price,
            quantity = product.quantity,
            unit = product.unit,
            description = product.description,
            imageUrl = product.imageUrl,
            farmerName = product.farmer.fullName
        )
    }
}