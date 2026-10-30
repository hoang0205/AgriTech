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
    var shippingCostUpdatedAt: LocalDateTime? = null,
    var sellerDeliveredAt: LocalDateTime? = null,
    var buyerReceivedAt: LocalDateTime? = null,
    var autoCompleteAt: LocalDateTime? = null,
    var completedAt: LocalDateTime? = null,
    var completionMethod: String? = null,
    @Column(nullable = false, columnDefinition = "boolean default false")
    var complaintOpen: Boolean = false,
    var complaintId: Long? = null
)

enum class DeliveryMethod { SELF, GHN }

fun Order.isGhnSimulation(): Boolean = deliveryMethod == DeliveryMethod.SELF &&
    ghnEnvironment == "staging" && ghnStatus == "simulation"

fun Order.hasActiveGhnShipment(): Boolean = ghnRequestJson != null && ghnStatus != "cancel" && !isGhnSimulation()

fun Order.isAwaitingOnlinePayment(): Boolean = status == "UNPAID" ||
    (paymentMethod == "VNPAY" && vnpayTransactionNo.isNullOrBlank())

fun Order.requirePaymentForFulfillment() {
    if (isAwaitingOnlinePayment()) {
        throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
            "Đơn hàng chưa được xác nhận thanh toán. Vui lòng chờ người mua thanh toán thành công.")
    }
}

fun Order.completionDeadline(): LocalDateTime? = autoCompleteAt ?: sellerDeliveredAt?.plusHours(48)

fun Order.markCompleted(method: String, at: LocalDateTime = LocalDateTime.now()) {
    status = "COMPLETED"
    completedAt = at
    completionMethod = method
}
