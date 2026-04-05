package com.uet.agritech.product

import com.uet.agritech.user.User
import jakarta.persistence.*

@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var category: String,

    @Column(nullable = false)
    var price: Double,

    @Column(nullable = false)
    var quantity: Double,

    @Column(nullable = false)
    var unit: String,

    @Column(columnDefinition = "TEXT")
    var description: String,

    @ElementCollection
    @CollectionTable(
        name = "product_images",
        joinColumns = [JoinColumn(name = "product_id")]
    )
    @Column(name = "image_url")
    var imageUrls: MutableList<String> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farmer_id", nullable = false)
    var farmer: User
)