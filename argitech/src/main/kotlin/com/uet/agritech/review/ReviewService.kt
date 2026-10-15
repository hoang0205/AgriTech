package com.uet.agritech.review

import com.uet.agritech.order.OrderItemRepository
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.review.dto.ReviewRequestDto
import com.uet.agritech.review.dto.ReviewResponseDto
import com.uet.agritech.review.dto.ReviewSummaryDto
import com.uet.agritech.user.UserRepository // Import UserRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable // Import Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import kotlin.concurrent.thread
import kotlin.math.round // Import hàm làm tròn số

@Service
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val aiServiceClient: AiServiceClient,
    private val entityManager: EntityManager,
    private val productSummaryWriter: ProductSummaryWriter,
    @Qualifier("aiSummaryExecutor")
    private val aiSummaryExecutor: TaskExecutor,
    private val orderItemRepository: OrderItemRepository,
) {
    @Transactional
    fun addReview(
        userId: String,
        dto: ReviewRequestDto
    ): ReviewResponseDto {
        val product = productRepository.findForStockUpdate(dto.productId)
            .orElseThrow {
                RuntimeException("Sản phẩm không tồn tại với ID: ${dto.productId}")
            }

        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE)

        val reviewer = userRepository.findByPhoneNumber(userId)
            .orElseThrow {
                RuntimeException("Tài khoản đánh giá không tồn tại")
            }

        val completedPurchases =
            orderItemRepository.countCompletedPurchases(
                phone = reviewer.phoneNumber,
                productId = dto.productId
            )

        if (completedPurchases == 0L) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Bạn chỉ được đánh giá sản phẩm đã nhận thành công"
            )
        }

        val isAlreadyReviewed = reviewRepository.existsByProductIdAndUserId(dto.productId, userId)
        if (isAlreadyReviewed) {
            throw RuntimeException("Bạn đã đánh giá sản phẩm này rồi!")
        }

        if (dto.rating !in 1..5) {
            throw RuntimeException("Điểm đánh giá phải từ 1 đến 5")
        }

        val review = Review(
            product = product,
            userId = userId,
            rating = dto.rating,
            comment = dto.comment,
            imageUrls = dto.imageUrls
        )
        val savedReview = reviewRepository.save(review)

        val totalReviews = reviewRepository.countByProductId(product.id.toString()).toInt()
        val avgRating = reviewRepository.getAverageRatingByProductId(product.id.toString()) ?: 0.0

        val roundedRating = round(avgRating * 10.0) / 10.0

        product.reviewCount = totalReviews
        product.rating = roundedRating
        productRepository.save(product)

        if (totalReviews >= 3) {
            val productId = product.id.toString()
            val productName = product.name

            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        aiSummaryExecutor.execute {
                            triggerAiReviewSummary(productId, productName)
                        }
                    }
                }
            )
        }

        return ReviewResponseDto(
            id = savedReview.id,
            productId = requireNotNull(product.id),
            userId = userId,
            userName = reviewer.fullName,
            rating = savedReview.rating,
            comment = savedReview.comment,
            imageUrls = savedReview.imageUrls,
            createdAt = savedReview.createdAt
        )
    }

    private fun triggerAiReviewSummary(productId: String, productName: String) {
        try {
            val pageable = PageRequest.of(0, 15, Sort.by("createdAt").descending())
            val recentReviews = reviewRepository.findByProductId(productId, pageable)

            val validComments = recentReviews.content
                .mapNotNull { it.comment }
                .filter { it.isNotBlank() }

            if (validComments.size >= 3) {
                val aiResponse = aiServiceClient.summarizeReviews(productName, validComments)

                if (aiResponse != null && aiResponse.success) {
                    productSummaryWriter.update(
                        productId = productId,
                        pros = aiResponse.pros.joinToString("\n"),
                        cons = aiResponse.cons.joinToString("\n")
                    )

                    println(
                        "--> Đã cập nhật thành công đánh giá tổng quát " +
                                "cho sản phẩm: $productName"
                    )
                }
            }
        } catch (e: Exception) {
            println("Lỗi trong quá trình chạy ngầm AI review summary: ${e.message}")
        }
    }

    fun getReviewsOfProduct(productId: String, pageable: Pageable): Page<ReviewResponseDto> {
        val reviews = reviewRepository.findByProductId(productId, pageable)

        return reviews.map { review ->
            val user = userRepository.findByPhoneNumber(review.userId).orElse(null)
            val fullName = user?.fullName ?: "Người dùng ẩn danh"

            ReviewResponseDto(
                id = review.id,
                productId = productId,
                userId = review.userId,
                userName = fullName,
                rating = review.rating,
                comment = review.comment,
                imageUrls = review.imageUrls,
                createdAt = review.createdAt
            )
        }
    }

    fun getReviewSummary(productId: String): ReviewSummaryDto {
        val totalReviews = reviewRepository.countByProductId(productId).toInt()
        val avgRating = reviewRepository.getAverageRatingByProductId(productId) ?: 0.0
        val roundedRating = kotlin.math.round(avgRating * 10.0) / 10.0

        if (totalReviews == 0) {
            return ReviewSummaryDto(0, 0.0, 0, 0, 0, 0, 0)
        }

        return ReviewSummaryDto(
            totalReviews = totalReviews,
            averageRating = roundedRating,
            star5 = reviewRepository.countByProductIdAndRating(productId, 5).toInt(),
            star4 = reviewRepository.countByProductIdAndRating(productId, 4).toInt(),
            star3 = reviewRepository.countByProductIdAndRating(productId, 3).toInt(),
            star2 = reviewRepository.countByProductIdAndRating(productId, 2).toInt(),
            star1 = reviewRepository.countByProductIdAndRating(productId, 1).toInt()
        )
    }
}