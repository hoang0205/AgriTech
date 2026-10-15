package com.uet.agritech.user

import com.uet.agritech.product.Product
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.product.dto.ProductResponse
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.collections.get

@Service
class RecommendationService(
    private val interactionRepository: UserInteractionRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(RecommendationService::class.java)
    }

    @Transactional(readOnly = true)
    fun getMixedRecommendations(
        phone: String,
        limit: Int = 10
    ): List<ProductResponse> {
        val size = limit.coerceIn(1, 50)

        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { IllegalArgumentException("User không tồn tại") }

        val personal = loadSafely("personal") {
            interactionRepository
                .findTopProductsByUser(user, PageRequest.of(0, size))
                .map { toResponse(it) }
        }

        val category = loadSafely("category") {
            getCategoryRecommendations(user, size)
                .map { toResponse(it) }
        }

        val similar = loadSafely("similar") {
            getSimilarRecommendations(user, size)
                .map { toResponse(it) }
        }

        val result = mutableListOf<ProductResponse>()
        val seenIds = mutableSetOf<String>()

        fun add(product: ProductResponse) {
            if (result.size < size && seenIds.add(product.id)) {
                result.add(product)
            }
        }

        val sources = listOf(personal, category, similar)
        val maxLength = sources.maxOf { it.size }

        for (index in 0 until maxLength) {
            for (source in sources) {
                source.getOrNull(index)?.let { add(it) }
            }

            if (result.size >= size) break
        }

        if (result.size < size) {
            val fallback = loadSafely("fallback") {
                productRepository
                    .findAvailableRecommendationFallback(
                        PageRequest.of(0, size + seenIds.size)
                    )
                    .map { toResponse(it) }
            }

            fallback.forEach { add(it) }
        }

        return result
    }

    private fun getCategoryRecommendations(
        user: User,
        limit: Int
    ): List<Product> {
        val categories = interactionRepository
            .findFavoriteCategoriesByUser(user, PageRequest.of(0, 3))
            .mapNotNull { it.getOrNull(0) as? String }

        val sources = categories.map { category ->
            productRepository.findUnseenAvailableProductsByCategory(
                category,
                user,
                PageRequest.of(0, limit)
            )
        }

        return interleaveProducts(sources, limit)
    }

    private fun getSimilarRecommendations(
        user: User,
        limit: Int
    ): List<Product> {
        val similarUsers = interactionRepository
            .findSimilarUsersByCategories(user, PageRequest.of(0, 5))

        val sources = similarUsers.map { similarUser ->
            interactionRepository.findUnseenProductsFromSimilarUser(
                similarUser,
                user,
                PageRequest.of(0, limit)
            )
        }

        return interleaveProducts(sources, limit)
    }

    private fun interleaveProducts(
        sources: List<List<Product>>,
        limit: Int
    ): List<Product> {
        val result = mutableListOf<Product>()
        val seenIds = mutableSetOf<String>()
        val maxLength = sources.maxOfOrNull { it.size } ?: 0

        for (index in 0 until maxLength) {
            for (source in sources) {
                val product = source.getOrNull(index) ?: continue
                val id = product.id ?: continue

                if (seenIds.add(id)) {
                    result.add(product)
                    if (result.size >= limit) return result
                }
            }
        }

        return result
    }

    private fun loadSafely(
        source: String,
        block: () -> List<ProductResponse>
    ): List<ProductResponse> {
        return try {
            block()
        } catch (e: Exception) {
            logger.error("Không lấy được nguồn gợi ý: $source", e)
            emptyList()
        }
    }

    private fun toResponse(product: Product): ProductResponse {
        return ProductResponse(
            id = requireNotNull(product.id),
            name = product.name,
            category = product.category,
            price = product.price,
            quantity = product.quantity,
            unit = product.unit,
            description = product.description,
            imageUrls = product.imageUrls.toMutableList(),
            farmerName = product.farmer.fullName,
            rating = product.rating,
            reviewCount = product.reviewCount,
            farmerId = requireNotNull(product.farmer.id),
            farmerAvatar = product.farmer.avatarUrl ?: "",
            farmerPhone = product.farmer.phoneNumber ?: ""
        )
    }

    fun recordInteraction(
        userPhone: String,
        product: Product,
        actionType: String,
        weight: Double = 1.0
    ) {
        try {
            val user = userRepository.findByPhoneNumber(userPhone)
                .orElseThrow { RuntimeException("User không tồn tại") }

            val interaction = UserInteraction(
                user = user,
                product = product,
                actionType = actionType,
                weight = weight,
                createdAt = LocalDateTime.now()
            )
            interactionRepository.save(interaction)
        } catch (e: Exception) {
            logger.error("Lỗi ghi nhận tương tác: ${e.message}")
        }
    }
}
