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

    var vnpayTxnRef: String? = null,
    var vnpayTransactionNo: String? = null,

    @Column(nullable = false)
    val orderDate: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false, columnDefinition = "bigint default 0")
    val shippingFee: Long = 0,

    @Column(length = 1000)
    val pickupAddress: String? = null,

    @Column
    val pickupLatitude: Double? = null,

    @Column
    val pickupLongitude: Double? = null,

    @Column
    val deliveryLatitude: Double? = null,

    @Column
    val deliveryLongitude: Double? = null,

    @Column
    val deliveryDistanceKm: Double? = null,

    val receiverName: String? = null,
    val paymentMethod: String? = null,
    var ghnOrderCode: String? = null,
    var ghnStatus: String? = null,
    var ghnShippingFee: Long? = null,
    var ghnExpectedDeliveryTime: String? = null,
    var ghnUpdatedAt: LocalDateTime? = null,

    @get:com.fasterxml.jackson.annotation.JsonIgnore
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    var ghnRequestJson: String? = null,
    var ghnShopId: Int? = null,
    var ghnEnvironment: String? = null,

    @Enumerated(EnumType.STRING)
    var deliveryMethod: DeliveryMethod? = null,
    var actualShippingCost: Long? = null,
    var shippingCostUpdatedAt: LocalDateTime? = null
)

enum class DeliveryMethod { SELF, GHN }

fun Order.isGhnSimulation(): Boolean = deliveryMethod == DeliveryMethod.SELF &&
    ghnEnvironment == "staging" && ghnStatus == "simulation"

fun Order.hasActiveGhnShipment(): Boolean = ghnRequestJson != null && ghnStatus != "cancel" && !isGhnSimulation()
