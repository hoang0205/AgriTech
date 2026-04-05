package com.uet.agritech.order

import com.uet.agritech.user.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val totalAmount: Double,

    @Column(nullable = false)
    val shippingAddress: String,

    @Column(nullable = false)
    val phoneNumber: String,

    @Column(nullable = false)
    var status: String = "PENDING",

    @Column(nullable = false)
    val orderDate: LocalDateTime = LocalDateTime.now()
)