package com.uet.agritech.review

import com.uet.agritech.product.ProductRepository
import com.uet.agritech.review.dto.ReviewRequestDto
import com.uet.agritech.review.dto.ReviewResponseDto
import com.uet.agritech.user.UserRepository // Import UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable // Import Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.round // Import hàm làm tròn số

@Service
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun addReview(userId: String, dto: ReviewRequestDto): Review {
        val product = productRepository.findById(dto.productId)
            .orElseThrow { RuntimeException("Sản phẩm không tồn tại với ID: ${dto.productId}") }

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

        return savedReview
    }

    fun getReviewsOfProduct(productId: String, pageable: Pageable): Page<ReviewResponseDto> {
        val reviews = reviewRepository.findByProductId(productId, pageable)

        return reviews.map { review ->
            val user = userRepository.findById(review.userId).orElse(null)
            val fullName = user?.fullName ?: "Người dùng ẩn danh"

            ReviewResponseDto(
                id = review.id,
                userId = review.userId,
                userName = fullName,
                rating = review.rating,
                comment = review.comment,
                imageUrls = review.imageUrls,
                createdAt = review.createdAt
            )
        }
    }
}