package com.uet.agritech.user

import jakarta.persistence.*

@Entity
@Table(name = "shipping_addresses")
class ShippingAddress(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var receiverName: String,

    @Column(nullable = false)
    var phoneNumber: String,

    @Column(nullable = false)
    var addressDetail: String,

    @Column(nullable = false)
    var isDefault: Boolean = false
)