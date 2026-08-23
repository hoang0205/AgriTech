package com.uet.agritech.review

import com.uet.agritech.product.Product
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "reviews")
data class Review(
    @Id
    @Column(length = 36)
    val id: String = java.util.UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false)
    val userId: String,

    @Column(nullable = false)
    val rating: Int,

    @Column(columnDefinition = "TEXT")
    val comment: String? = null,

    @ElementCollection
    @CollectionTable(
        name = "review_images",
        joinColumns = [JoinColumn(name = "review_id")]
    )
    @Column(name = "image_url")
    val imageUrls: List<String> = mutableListOf(),

    val createdAt: LocalDateTime = LocalDateTime.now()
)