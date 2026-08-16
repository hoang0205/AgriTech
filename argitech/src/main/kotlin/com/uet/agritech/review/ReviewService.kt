package com.uet.agritech.review

import com.uet.agritech.product.ProductRepository
import com.uet.agritech.product.ProductService
import com.uet.agritech.review.dto.ReviewRequestDto
import com.uet.agritech.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.Exception
import java.lang.RuntimeException

@Service
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val productRepository: ProductRepository,
    private val productService: ProductService
) {
    @Transactional
    fun addReview(userId: String, dto: ReviewRequestDto): Review {
        try {
            val productExists = productRepository.existsById(dto.productId)
            if (!productExists) {
                throw RuntimeException("Sản phẩm không tồn tại với ID: ${dto.productId}")
            }

            if (dto.rating !in 1..5) {
                throw RuntimeException("Điểm đánh giá phải từ 1 đến 5")
            }

            val review = Review(
                productId = dto.productId,
                userId = userId,
                rating = dto.rating,
                comment = dto.comment,
                imageUrls = dto.imageUrls
            )

            val savedReview = reviewRepository.save(review)

            productService.updateProductRating(dto.productId, dto.rating)

            return savedReview

        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Đã xảy ra lỗi hệ thống khi lưu đánh giá: ${e.message}")
        }
    }

    fun getReviewsByProduct(productId: String, page: Int, size: Int): Page<Review> {
        try {
            val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
            return reviewRepository.findByProductId(productId, pageable)
        } catch (e: Exception) {
            throw RuntimeException("Lỗi khi lấy danh sách đánh giá: ${e.message}")
        }
    }
}