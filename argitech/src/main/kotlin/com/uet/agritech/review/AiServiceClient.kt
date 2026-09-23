package com.uet.agritech.review

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

data class AiReviewSummarizeRequest(
    val product_name: String,
    val reviews: List<String>
)

data class AiReviewSummarizeResponse(
    val success: Boolean,
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val error: String? = null
)

@Service
class AiServiceClient {
    private val restTemplate = RestTemplate()
    private val aiServerUrl = "http://localhost:8000/api/summarize-reviews"

    fun summarizeReviews(productName: String, reviews: List<String>): AiReviewSummarizeResponse? {
        val payload = AiReviewSummarizeRequest(
            product_name = productName,
            reviews = reviews
        )
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val entity = HttpEntity(payload, headers)

        return try {
            restTemplate.postForObject(aiServerUrl, entity, AiReviewSummarizeResponse::class.java)
        } catch (e: Exception) {
            println("Lỗi gọi sang FastAPI Service: ${e.message}")
            null
        }
    }
}