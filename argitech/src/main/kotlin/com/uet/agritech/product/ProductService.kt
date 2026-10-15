package com.uet.agritech.product

import com.uet.agritech.product.dto.ProductRequest
import com.uet.agritech.product.dto.ProductResponse
import com.uet.agritech.user.RecommendationService
import com.uet.agritech.user.Role
import com.uet.agritech.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val interactionService: RecommendationService
) {

    @Transactional
    fun createProduct(request: ProductRequest): ProductResponse {
        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication == null ||
            !authentication.isAuthenticated ||
            authentication.name == "anonymousUser"
        ) {
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Bạn cần đăng nhập"
            )
        }

        val farmer = userRepository.findByPhoneNumber(authentication.name)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tài khoản không tồn tại"
                )
            }

        if (farmer.role != Role.FARMER && farmer.role != Role.ADMIN) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Tài khoản không có quyền đăng bán sản phẩm"
            )
        }

        val name = request.name.trim()
        val category = request.category.trim()
        val unit = request.unit.trim()

        require(name.isNotBlank() && name.length <= 255) {
            "Tên sản phẩm phải có từ 1 đến 255 ký tự"
        }

        val validCategories = setOf(
            "Rau củ",
            "Trái cây",
            "Thịt",
            "Thủy hải sản",
            "Sản phẩm khác",
            "Vegetables",
            "Fruits",
            "Meat",
            "Seafood",
            "Others"
        )

        require(category in validCategories) {
            "Danh mục sản phẩm không hợp lệ"
        }

        require(unit.isNotBlank() && unit.length <= 255) {
            "Đơn vị tính phải có từ 1 đến 255 ký tự"
        }

        require(request.price.isFinite() && request.price > 0.0) {
            "Giá sản phẩm phải là số hợp lệ và lớn hơn 0"
        }

        require(request.quantity.isFinite() && request.quantity >= 0.0) {
            "Số lượng tồn phải là số hợp lệ và không được âm"
        }

        val product = Product(
            name = name,
            category = category,
            price = request.price,
            quantity = request.quantity,
            unit = unit,
            description = request.description.trim(),
            imageUrls = request.imageUrls.toMutableList(),
            farmer = farmer
        )

        val savedProduct = productRepository.save(product)

        return ProductResponse(
            id = requireNotNull(savedProduct.id),
            name = savedProduct.name,
            category = savedProduct.category,
            price = savedProduct.price,
            quantity = savedProduct.quantity,
            unit = savedProduct.unit,
            description = savedProduct.description,
            imageUrls = savedProduct.imageUrls,
            farmerName = farmer.fullName,
            rating = savedProduct.rating,
            reviewCount = savedProduct.reviewCount,
            farmerId = requireNotNull(farmer.id),
            farmerPhone = farmer.phoneNumber,
            farmerAvatar = farmer.avatarUrl ?: ""
        )
    }

    fun getAllProducts(page: Int, size: Int): Page<ProductResponse> {
        val pageable = PageRequest.of(page, size, Sort.by("id").ascending())

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
                imageUrls = product.imageUrls,
                farmerName = product.farmer.fullName,
                rating = product.rating,
                reviewCount = product.reviewCount,
                farmerId = product.farmer.id!!,
                farmerPhone = product.farmer.phoneNumber,
                farmerAvatar = product.farmer.avatarUrl ?: ""
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
                imageUrls = product.imageUrls,
                farmerName = product.farmer.fullName,
                rating = product.rating,
                reviewCount = product.reviewCount,
                farmerId = product.farmer.id!!,
                farmerPhone = product.farmer.phoneNumber,
                farmerAvatar = product.farmer.avatarUrl ?: ""
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
                imageUrls = product.imageUrls,
                farmerName = product.farmer.fullName,
                rating = product.rating,
                reviewCount = product.reviewCount,
                farmerId = product.farmer.id!!,
                farmerPhone = product.farmer.phoneNumber,
                farmerAvatar = product.farmer.avatarUrl ?: ""
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
                imageUrls = product.imageUrls,
                farmerName = product.farmer.fullName,
                rating = product.rating,
                reviewCount = product.reviewCount,
                farmerId = product.farmer.id!!,
                farmerPhone = product.farmer.phoneNumber,
                farmerAvatar = product.farmer.avatarUrl ?: ""
            )
        }
    }

    fun suggestProductNames(keyword: String): List<String> {
        return productRepository.suggestProductNames(keyword)
    }

    fun getProductById(id: String): ProductResponse {
        val product = productRepository.findById(id)
            .orElseThrow { RuntimeException("Không tìm thấy sản phẩm này hoặc đã bị xóa!") }

        val userPhone = SecurityContextHolder.getContext().authentication?.name
        if (userPhone != null) {
            interactionService.recordInteraction(userPhone, product, "VIEW", 1.0)
        }

        return ProductResponse(
            id = product.id!!,
            name = product.name,
            category = product.category,
            price = product.price,
            quantity = product.quantity,
            unit = product.unit,
            description = product.description,
            imageUrls = product.imageUrls,
            farmerName = product.farmer.fullName,
            rating = product.rating,
            reviewCount = product.reviewCount,
            farmerId = product.farmer.id!!,
            farmerPhone = product.farmer.phoneNumber,
            farmerAvatar = product.farmer.avatarUrl ?: "",
            aiPros = product.aiPros,
            aiCons = product.aiCons
        )
    }

    fun getProductsByFarmerId(farmerId: String, page: Int, size: Int): Page<ProductResponse> {
        val pageable = PageRequest.of(page, size, Sort.by("id").descending())
        val productPage = productRepository.findByFarmerId(farmerId, pageable)

        return productPage.map { product ->
            ProductResponse(
                id = product.id!!,
                name = product.name,
                category = product.category,
                price = product.price,
                quantity = product.quantity,
                unit = product.unit,
                description = product.description,
                imageUrls = product.imageUrls,
                farmerName = product.farmer.fullName,
                rating = product.rating,
                reviewCount = product.reviewCount,
                farmerId = product.farmer.id!!,
                farmerPhone = product.farmer.phoneNumber,
                farmerAvatar = product.farmer.avatarUrl ?: ""
            )
        }
    }
}