package com.uet.agritech.order

import com.uet.agritech.notification.NotificationService
import jakarta.persistence.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.time.LocalDateTime

enum class OrderComplaintStatus { OPEN, REVIEWING, RESOLVED, REJECTED }
enum class OrderComplaintReason { NOT_RECEIVED, DAMAGED, MISSING_OR_WRONG, OTHER }
enum class OrderComplaintOutcome { KEEP_ORDER, COMPLETE_ORDER, CANCEL_ORDER }

@Embeddable
class OrderComplaintEvent(
    var status: String = "OPEN",
    @Column(length = 2000) var message: String = "",
    var occurredAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "order_complaints", uniqueConstraints = [UniqueConstraint(columnNames = ["order_id"])])
class OrderComplaint(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @Column(name = "order_id", nullable = false) val orderId: Long,
    val reporterId: String,
    val reporterName: String,
    @Enumerated(EnumType.STRING) val reason: OrderComplaintReason,
    @Column(length = 2000) val description: String,
    @ElementCollection @CollectionTable(name = "order_complaint_images", joinColumns = [JoinColumn(name = "complaint_id")])
    @Column(length = 2048) val imageUrls: List<String> = emptyList(),
    @Enumerated(EnumType.STRING) var status: OrderComplaintStatus = OrderComplaintStatus.OPEN,
    @Column(length = 2000) var adminReply: String? = null,
    var handledBy: String? = null,
    var outcome: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    @ElementCollection @CollectionTable(name = "order_complaint_events", joinColumns = [JoinColumn(name = "complaint_id")])
    @OrderColumn(name = "event_index") val events: MutableList<OrderComplaintEvent> = mutableListOf()
)

interface OrderComplaintRepository : JpaRepository<OrderComplaint, Long> {
    fun findByOrderId(orderId: Long): OrderComplaint?
    @org.springframework.data.jpa.repository.Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from OrderComplaint c where c.orderId = :orderId")
    fun findForOrderUpdate(@org.springframework.data.repository.query.Param("orderId") orderId: Long): OrderComplaint?
    fun findAllByStatusOrderByCreatedAtDesc(status: OrderComplaintStatus, pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<OrderComplaint>
    fun findAllByOrderByCreatedAtDesc(pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<OrderComplaint>
}

data class CreateOrderComplaintRequest(val reason: OrderComplaintReason, val description: String, val imageUrls: List<String> = emptyList())
data class UpdateOrderComplaintRequest(val status: OrderComplaintStatus, val reply: String,
    val outcome: OrderComplaintOutcome = OrderComplaintOutcome.KEEP_ORDER)
data class OrderComplaintEventResponse(val status: String, val message: String, val occurredAt: String)
data class OrderComplaintResponse(val id: Long, val orderId: Long, val reporterName: String,
    val reason: OrderComplaintReason, val description: String, val imageUrls: List<String>,
    val status: OrderComplaintStatus, val adminReply: String?, val createdAt: String, val updatedAt: String,
    val orderStatus: String, val sellerDeliveredAt: String?, val buyerReceivedAt: String?,
    val events: List<OrderComplaintEventResponse>, val outcome: String?,
    val totalAmount: Double, val receiverName: String, val shippingAddress: String,
    val products: List<String>)
data class OrderComplaintContext(val complaint: OrderComplaintResponse?, val canCreate: Boolean)

fun Order.canCreateComplaint(now: LocalDateTime = LocalDateTime.now()): Boolean =
    status == "SHIPPING" || status == "COMPLETED" && completedAt?.plusHours(48)?.let { now <= it } == true

@Service
class OrderComplaintService(private val complaints: OrderComplaintRepository, private val orders: OrderRepository,
    private val items: OrderItemRepository, private val notifications: NotificationService,
    private val entityManager: EntityManager) {

    @Transactional(readOnly = true)
    fun context(id: Long, phone: String): OrderComplaintContext {
        val order = orders.findById(id).orElseThrow { missing() }
        val buyer = order.user.phoneNumber == phone
        val lines = items.findAllByOrder(order)
        if (!buyer && (lines.isEmpty() || lines.any { it.product.farmer.phoneNumber != phone })) forbidden()
        val complaint = complaints.findByOrderId(id)
        return OrderComplaintContext(complaint?.let { response(it, order) }, buyer && complaint == null && order.canCreateComplaint())
    }

    @Transactional
    fun create(id: Long, phone: String, request: CreateOrderComplaintRequest): OrderComplaintResponse {
        val order = orders.findForStatusUpdate(id).orElseThrow { missing() }
        if (order.user.phoneNumber != phone) forbidden()
        if (!order.canCreateComplaint()) conflict("Chỉ khiếu nại đơn đang giao hoặc trong 48 giờ sau khi hoàn thành.")
        if (complaints.findByOrderId(id) != null) conflict("Đơn đã có khiếu nại. Vui lòng xem kết quả xử lý.")
        val text = request.description.trim()
        require(text.length in 10..2000) { "Mô tả khiếu nại cần từ 10 đến 2000 ký tự." }
        require(request.imageUrls.size <= 3 && request.imageUrls.distinct().size == request.imageUrls.size) { "Chỉ đính kèm tối đa 3 ảnh khác nhau." }
        require(request.imageUrls.all { url -> url.length <= 2048 && runCatching {
            val uri = java.net.URI(url); uri.scheme == "https" && !uri.host.isNullOrBlank()
        }.getOrDefault(false) }) { "Đường dẫn ảnh không hợp lệ." }
        val complaint = complaints.save(OrderComplaint(orderId = id, reporterId = requireNotNull(order.user.id),
            reporterName = order.user.fullName, reason = request.reason, description = text, imageUrls = request.imageUrls,
            events = mutableListOf(OrderComplaintEvent(message = "Người mua đã gửi khiếu nại."))))
        order.complaintOpen = true
        order.complaintId = complaint.id
        orders.save(order)
        notifyParties(order, "Đơn hàng có khiếu nại", "Khiếu nại đơn #$id đã được ghi nhận. Tạm dừng hoàn thành đơn trong khi admin xử lý.")
        return response(complaint, order)
    }

    @Transactional(readOnly = true)
    fun list(status: OrderComplaintStatus?, page: Int, size: Int): Map<String, Any> {
        require(page >= 0 && size in 1..100)
        val paging = PageRequest.of(page, size)
        val result = if (status == null) complaints.findAllByOrderByCreatedAtDesc(paging)
            else complaints.findAllByStatusOrderByCreatedAtDesc(status, paging)
        return mapOf("content" to result.content.map { response(it, orders.findById(it.orderId).orElseThrow { missing() }) },
            "totalElements" to result.totalElements)
    }

    @Transactional
    fun update(id: Long, admin: String, request: UpdateOrderComplaintRequest): OrderComplaintResponse {
        // Read only the immutable order id first; always lock order before changing the complaint.
        val reference = complaints.findById(id).orElseThrow { missing() }
        val order = orders.findForStatusUpdate(reference.orderId).orElseThrow { missing() }
        val orderId = reference.orderId
        entityManager.detach(reference)
        // A locking read must hydrate a fresh entity, not refresh a cached entity
        // through an older REPEATABLE READ snapshot after another admin commits.
        val complaint = complaints.findForOrderUpdate(orderId) ?: throw missing()
        if (complaint.status in setOf(OrderComplaintStatus.RESOLVED, OrderComplaintStatus.REJECTED)) conflict("Khiếu nại đã đóng.")
        require(request.status != OrderComplaintStatus.OPEN) { "Không thể đưa khiếu nại về trạng thái mới." }
        val reply = request.reply.trim()
        require(reply.length in 5..2000) { "Phản hồi cần từ 5 đến 2000 ký tự." }
        require(request.outcome == OrderComplaintOutcome.KEEP_ORDER || request.status == OrderComplaintStatus.RESOLVED)
        if (request.outcome == OrderComplaintOutcome.COMPLETE_ORDER) {
            order.requirePaymentForFulfillment()
            require(order.status in setOf("SHIPPING", "COMPLETED") && order.sellerDeliveredAt != null) { "Chưa đủ thông tin giao hàng để hoàn thành đơn." }
            if (order.status != "COMPLETED") order.markCompleted("ADMIN")
        }
        if (request.outcome == OrderComplaintOutcome.CANCEL_ORDER) {
            require(order.status in setOf("SHIPPING", "COMPLETED")) { "Đơn không thể đóng theo kết quả khiếu nại." }
            // Shipped produce is not assumed to have returned to saleable inventory.
            order.status = "CANCELLED"
        }
        val closed = request.status in setOf(OrderComplaintStatus.RESOLVED, OrderComplaintStatus.REJECTED)
        complaint.status = request.status
        complaint.adminReply = reply
        complaint.handledBy = admin
        if (closed) complaint.outcome = request.outcome.name
        complaint.updatedAt = LocalDateTime.now()
        complaint.events.add(OrderComplaintEvent(request.status.name, reply, complaint.updatedAt))
        order.complaintOpen = !closed
        if (closed && order.status == "SHIPPING" && order.sellerDeliveredAt != null) {
            // Give the buyer a fresh response window after the dispute has been closed.
            order.autoCompleteAt = complaint.updatedAt.plusHours(48)
        }
        complaints.save(complaint)
        orders.save(order)
        notifyParties(order, "Cập nhật khiếu nại đơn hàng", "Đơn #${order.id}: $reply")
        return response(complaint, order)
    }

    private fun notifyParties(order: Order, title: String, body: String) {
        notifications.sendOrderStatusNotification(order.user, requireNotNull(order.id), order.status, title, body)
        items.findAllByOrder(order).map { it.product.farmer }.distinctBy { it.id }.forEach {
            notifications.sendOrderStatusNotification(it, requireNotNull(order.id), "RECEIVED_BY_BUYER", title, body)
        }
    }
    private fun response(c: OrderComplaint, o: Order) = OrderComplaintResponse(requireNotNull(c.id), c.orderId,
        c.reporterName, c.reason, c.description, c.imageUrls, c.status, c.adminReply, c.createdAt.toString(),
        c.updatedAt.toString(), o.status, o.sellerDeliveredAt?.toString(), o.buyerReceivedAt?.toString(),
        c.events.map { OrderComplaintEventResponse(it.status, it.message, it.occurredAt.toString()) }, c.outcome,
        o.totalAmount, o.receiverName ?: o.user.fullName, o.shippingAddress,
        items.findAllByOrder(o).map { "${it.product.name} • ${it.quantity} ${it.product.unit}" })
    private fun missing() = ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng hoặc khiếu nại.")
    private fun forbidden(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xử lý khiếu nại này.")
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
}

@RestController
@RequestMapping("/api/orders/{orderId}/complaint")
class OrderComplaintController(private val service: OrderComplaintService) {
    @GetMapping fun context(@PathVariable orderId: Long, principal: Principal) = service.context(orderId, principal.name)
    @PostMapping fun create(@PathVariable orderId: Long, principal: Principal, @RequestBody request: CreateOrderComplaintRequest) =
        service.create(orderId, principal.name, request)
}

@RestController
@RequestMapping("/api/admin/order-complaints")
class AdminOrderComplaintController(private val service: OrderComplaintService) {
    @GetMapping fun list(@RequestParam(required = false) status: OrderComplaintStatus?,
        @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "10") size: Int) = service.list(status, page, size)
    @PatchMapping("/{id}") fun update(@PathVariable id: Long, principal: Principal,
        @RequestBody request: UpdateOrderComplaintRequest) = service.update(id, principal.name, request)
}
