package com.uet.agritech.review

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
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
class AiServiceClient(
    @Value("\${ai.base-url}") private val aiBaseUrl: String
) {
    private val restTemplate = RestTemplate(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5_000)
            setReadTimeout(240_000)
        }
    )

    private val aiServerUrl =
        "${aiBaseUrl.trimEnd('/')}/api/summarize-reviews"

    fun summarizeReviews(
        productName: String,
        reviews: List<String>
    ): AiReviewSummarizeResponse? {
        val payload = AiReviewSummarizeRequest(
            product_name = productName,
            reviews = reviews
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        return try {
            restTemplate.postForObject(
                aiServerUrl,
                HttpEntity(payload, headers),
                AiReviewSummarizeResponse::class.java
            )
        } catch (e: Exception) {
            println("Lỗi gọi sang FastAPI Service: ${e.message}")
            null
        }
    }
}