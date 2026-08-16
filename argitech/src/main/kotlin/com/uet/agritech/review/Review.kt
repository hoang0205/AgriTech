package com.uet.agritech.review

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "reviews")
data class Review(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(nullable = false)
    val productId: String,

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