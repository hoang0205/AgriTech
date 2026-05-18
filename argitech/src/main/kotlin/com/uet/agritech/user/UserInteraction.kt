package com.uet.agritech.user

import com.uet.agritech.product.Product
import com.uet.agritech.user.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_interactions")
class UserInteraction(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(nullable = false)
    val actionType: String,

    @Column(nullable = false)
    val weight: Double,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)