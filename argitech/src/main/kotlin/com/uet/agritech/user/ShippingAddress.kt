package com.uet.agritech.user

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*

@Entity
@Table(name = "shipping_addresses")
class ShippingAddress(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
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

data class ShippingAddressResponse(
    val id: Long,
    val receiverName: String,
    val phoneNumber: String,
    val addressDetail: String,
    val isDefault: Boolean
)