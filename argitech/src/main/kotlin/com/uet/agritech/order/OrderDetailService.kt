package com.uet.agritech.order

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class OrderPartyDetail(val id: String, val name: String, val avatar: String?, val phone: String)

data class OrderLineDetail(
    val id: Long,
    val productId: String,
    val name: String,
    val image: String?,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,
    val subtotal: Double,
    val sellerName: String
)

data class OrderDetailResponse(
    val orderId: Long,
    val status: String,
    val orderDate: String,
    val viewerRole: String,
    val buyer: OrderPartyDetail,
    val sellers: List<OrderPartyDetail>,
    val receiverName: String,
    val receiverPhone: String,
    val shippingAddress: String,
    val pickupAddress: String?,
    val items: List<OrderLineDetail>,
    val subtotal: Double,
    val shippingFee: Long,
    val totalAmount: Double,
    val paymentMethod: String?,
    val paymentStatus: String,
    val transactionNo: String?,
    val deliveryMethod: String?,
    val actualShippingCost: Long?,
    val ghnOrderCode: String?,
    val ghnStatus: String?,
    val expectedDeliveryTime: String?,
    val canCancel: Boolean,
    val canPay: Boolean,
    val nextStatus: String?,
    val canManageDelivery: Boolean,
    val sellerDeliveredAt: String?,
    val buyerReceivedAt: String?,
    val autoCompleteAt: String?,
    val completedAt: String?,
    val completionMethod: String?,
    val complaintOpen: Boolean,
    val complaintId: Long?,
    val canComplain: Boolean
)

@Service
class OrderDetailService(
    private val orders: OrderRepository,
    private val items: OrderItemRepository
) {
    @Transactional(readOnly = true)
    fun get(orderId: Long, phone: String): OrderDetailResponse {
        val order = orders.findById(orderId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.")
        }
        val lines = items.findAllByOrder(order)
        val isBuyer = order.user.phoneNumber == phone
        // A seller must own every line; legacy mixed orders must not expose other shops' data.
        val isSeller = lines.isNotEmpty() && lines.all { it.product.farmer.phoneNumber == phone }
        if (!isBuyer && !isSeller) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đơn hàng này.")
        }
        val sellerView = !isBuyer && isSeller
        val activeShipment = order.hasActiveGhnShipment()
        val paymentReady = !order.isAwaitingOnlinePayment()
        fun party(user: com.uet.agritech.user.User) = OrderPartyDetail(
            requireNotNull(user.id), user.fullName, user.avatarUrl, user.phoneNumber
        )
        val nextStatus = if (!paymentReady || order.complaintOpen) null else if (order.status == "SHIPPING") {
            if (sellerView && order.sellerDeliveredAt == null || isBuyer && order.sellerDeliveredAt != null && order.buyerReceivedAt == null) "COMPLETED" else null
        } else if (sellerView && !activeShipment) when (order.status) {
            "PENDING" -> "CONFIRMED"
            "CONFIRMED" -> if (order.deliveryMethod == DeliveryMethod.SELF) "SHIPPING" else null
            "SHIPPING" -> if (order.deliveryMethod == DeliveryMethod.SELF || order.deliveryMethod == null) "COMPLETED" else null
            else -> null
        } else null
        return OrderDetailResponse(
            orderId = requireNotNull(order.id), status = order.status,
            orderDate = order.orderDate.toString(), viewerRole = if (sellerView) "SELLER" else "BUYER",
            buyer = party(order.user), sellers = lines.map { it.product.farmer }.distinctBy { it.id }.map(::party),
            receiverName = order.receiverName?.takeIf { it.isNotBlank() } ?: order.user.fullName,
            receiverPhone = order.phoneNumber, shippingAddress = order.shippingAddress,
            pickupAddress = order.pickupAddress,
            items = lines.map {
                OrderLineDetail(requireNotNull(it.id), requireNotNull(it.product.id), it.product.name,
                    it.product.imageUrls.firstOrNull(), it.quantity, it.product.unit, it.price,
                    it.quantity * it.price, it.product.farmer.fullName)
            },
            subtotal = lines.sumOf { it.quantity * it.price }, shippingFee = order.shippingFee,
            totalAmount = order.totalAmount, paymentMethod = order.paymentMethod,
            paymentStatus = when {
                order.vnpayTransactionNo != null -> "PAID"
                order.paymentMethod == "COD" -> "COD"
                order.paymentMethod == "VNPAY" -> "UNPAID"
                else -> "UNKNOWN"
            },
            transactionNo = order.vnpayTransactionNo, deliveryMethod = order.deliveryMethod?.name,
            actualShippingCost = if (sellerView) order.actualShippingCost else null,
            ghnOrderCode = order.ghnOrderCode, ghnStatus = order.ghnStatus,
            expectedDeliveryTime = order.ghnExpectedDeliveryTime,
            canCancel = !activeShipment && if (sellerView) paymentReady && order.status in listOf("PENDING", "CONFIRMED")
                else order.status in listOf("PENDING", "UNPAID"),
            canPay = isBuyer && order.status == "UNPAID" && order.paymentMethod == "VNPAY" && order.ghnRequestJson == null,
            nextStatus = nextStatus,
            canManageDelivery = sellerView && paymentReady && order.status in listOf("CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED"),
            sellerDeliveredAt = order.sellerDeliveredAt?.toString(), buyerReceivedAt = order.buyerReceivedAt?.toString(),
            autoCompleteAt = if (order.status == "SHIPPING" && !order.complaintOpen) order.completionDeadline()?.toString() else null,
            completedAt = order.completedAt?.toString(), completionMethod = order.completionMethod,
            complaintOpen = order.complaintOpen, complaintId = order.complaintId,
            canComplain = isBuyer && order.complaintId == null && order.canCreateComplaint()
        )
    }
}
