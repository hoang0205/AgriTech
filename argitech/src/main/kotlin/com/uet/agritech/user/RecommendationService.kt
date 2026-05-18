package com.uet.agritech.user

import com.uet.agritech.product.Product
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.product.dto.ProductResponse
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
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

    fun getPersonalizedRecommendations(phone: String, limit: Int = 10): List<ProductResponse> {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { IllegalArgumentException("User không tồn tại") }

        val topScoredItems = try {
            interactionRepository.findTopProductsByUser(user, PageRequest.of(0, limit))
        } catch (e: Exception) {
            logger.error("Lỗi khi lấy gợi ý: ${e.message}")
            emptyList()
        }

        if (topScoredItems.isEmpty()) {
            logger.warn("Không tìm thấy gợi ý cho user: $phone")
            return emptyList()
        }

        return topScoredItems.mapNotNull { row ->
            try {
                val product = row.getOrNull(0) as? Product ?: return@mapNotNull null
                ProductResponse(
                    id = product.id ?: return@mapNotNull null,
                    name = product.name,
                    category = product.category,
                    price = product.price,
                    quantity = product.quantity,
                    unit = product.unit,
                    description = product.description,
                    imageUrls = product.imageUrls,
                    farmerName = product.farmer.fullName
                )
            } catch (e: Exception) {
                logger.error("Lỗi mapping product: ${e.message}")
                null
            }
        }
    }

    fun getNewProductsInFavoriteCategories(phone: String, limit: Int = 10): List<ProductResponse> {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { IllegalArgumentException("User không tồn tại") }

        val favoriteCategories =
            interactionRepository.findFavoriteCategoriesByUser(user, PageRequest.of(0, 3))

        if (favoriteCategories.isEmpty()) {
            logger.info("User $phone chưa có danh mục yêu thích, trả về sản phẩm ngẫu nhiên")
            return getRandomProducts(limit)
        }

        val recommendations = mutableListOf<ProductResponse>()
        val viewedProductIds = interactionRepository.findViewedProductIdsByUser(user)

        for (category in favoriteCategories) {
            val categoryName = category[0] as String
            val newProducts = productRepository.findByCategoryNotInProductIds(
                categoryName,
                viewedProductIds,
                PageRequest.of(0, limit)
            )

            newProducts.forEach { product ->
                if (recommendations.size < limit) {
                    recommendations.add(
                        ProductResponse(
                            id = product.id!!,
                            name = product.name,
                            category = product.category,
                            price = product.price,
                            quantity = product.quantity,
                            unit = product.unit,
                            description = product.description,
                            imageUrls = product.imageUrls,
                            farmerName = product.farmer.fullName
                        )
                    )
                }
            }
        }

        return recommendations
    }

    fun getSimilarUserRecommendations(phone: String, limit: Int = 10): List<ProductResponse> {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { IllegalArgumentException("User không tồn tại") }

        val similarUsers =
            interactionRepository.findSimilarUsersByCategories(user, PageRequest.of(0, 5))

        if (similarUsers.isEmpty()) {
            logger.info("Không tìm thấy user tương tự, trả về sản phẩm ngẫu nhiên")
            return getRandomProducts(limit)
        }

        val viewedProductIds = interactionRepository.findViewedProductIdsByUser(user)
        val recommendations = mutableListOf<ProductResponse>()

        for (similarUserArray in similarUsers) {
            val similarUser = similarUserArray[0] as com.uet.agritech.user.User

            val theirFavoriteProducts = interactionRepository.findTopProductsByUser(
                similarUser,
                PageRequest.of(0, limit)
            )

            theirFavoriteProducts.forEach { row ->
                try {
                    val product = row.getOrNull(0) as? Product
                    if (product != null && !viewedProductIds.contains(product.id) && recommendations.size < limit) {
                        recommendations.add(
                            ProductResponse(
                                id = product.id!!,
                                name = product.name,
                                category = product.category,
                                price = product.price,
                                quantity = product.quantity,
                                unit = product.unit,
                                description = product.description,
                                imageUrls = product.imageUrls,
                                farmerName = product.farmer.fullName
                            )
                        )
                    }
                } catch (e: Exception) {
                    logger.error("Lỗi mapping product: ${e.message}")
                }
            }
        }

        return recommendations
    }

    fun getMixedRecommendations(phone: String, limit: Int = 10): List<ProductResponse> {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { IllegalArgumentException("User không tồn tại") }

        try {
            val personalizedRecs = getPersonalizedRecommendations(phone, limit)
            val newCategoryRecs = getNewProductsInFavoriteCategories(phone, limit)
            val similarUserRecs = getSimilarUserRecommendations(phone, limit)

            val mixedList = mutableListOf<ProductResponse>()
            val maxSize = maxOf(personalizedRecs.size, newCategoryRecs.size, similarUserRecs.size)
            val productIds = mutableSetOf<String>()

            for (i in 0 until maxSize) {
                if (i < personalizedRecs.size) {
                    val product = personalizedRecs[i]
                    if (!productIds.contains(product.id)) {
                        mixedList.add(product)
                        productIds.add(product.id)
                    }
                }

                if (i < newCategoryRecs.size && mixedList.size < limit) {
                    val product = newCategoryRecs[i]
                    if (!productIds.contains(product.id)) {
                        mixedList.add(product)
                        productIds.add(product.id)
                    }
                }

                if (i < similarUserRecs.size && mixedList.size < limit) {
                    val product = similarUserRecs[i]
                    if (!productIds.contains(product.id)) {
                        mixedList.add(product)
                        productIds.add(product.id)
                    }
                }

                if (mixedList.size >= limit) break
            }

            return mixedList.take(limit)
        } catch (e: Exception) {
            logger.error("Lỗi khi lấy gợi ý kết hợp: ${e.message}")
            return emptyList()
        }
    }

    private fun getRandomProducts(limit: Int): List<ProductResponse> {
        return productRepository.findRandomProducts(limit).map { product ->
            ProductResponse(
                id = product.id!!,
                name = product.name,
                category = product.category,
                price = product.price,
                quantity = product.quantity,
                unit = product.unit,
                description = product.description,
                imageUrls = product.imageUrls,
                farmerName = product.farmer.fullName
            )
        }
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
