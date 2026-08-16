package com.uet.agritech.review

import com.uet.agritech.review.dto.ReviewRequestDto
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/reviews")
class ReviewController(
    private val reviewService: ReviewService
) {
    @PostMapping
    fun addReview(@RequestBody dto: ReviewRequestDto): ResponseEntity<Review> {
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = authentication?.name

        val review = userId?.let { reviewService.addReview(it, dto) }
        return ResponseEntity.ok(review)
    }

    @GetMapping("/product/{productId}")
    fun getProductReviews(
        @PathVariable productId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<Page<Review>> {
        return ResponseEntity.ok(reviewService.getReviewsByProduct(productId, page, size))
    }
}