package com.uet.agritech.bargain

import com.uet.agritech.delivery.*
import com.uet.agritech.notification.NotificationInboxRepository
import com.uet.agritech.notification.UserNotification
import com.uet.agritech.notification.BargainNotifications
import com.uet.agritech.order.NegotiatedPurchase
import com.uet.agritech.order.OrderService
import com.uet.agritech.order.dto.CheckoutRequest
import com.uet.agritech.product.Product
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.*
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

enum class BargainStatus { PENDING, ACCEPTED, REJECTED, WITHDRAWN, EXPIRED, ORDERED }

@Entity
@Table(name = "bargain_offers", indexes = [Index(name = "idx_bargain_buyer", columnList = "buyer_id"),
    Index(name = "idx_bargain_seller", columnList = "seller_id")],
    uniqueConstraints = [UniqueConstraint(name = "uk_bargain_order", columnNames = ["order_id"])])
class BargainOffer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "buyer_id", nullable = false) val buyer: User,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "seller_id", nullable = false) val seller: User,
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) val product: Product,
    @Column(nullable = false) val productName: String,
    @Column(nullable = false) val unit: String,
    @Column(nullable = false) val quantity: Double,
    @Column(nullable = false, precision = 19, scale = 0) val unitPrice: BigDecimal,
    @Column(nullable = false) val listedPrice: Double,
    @Column(nullable = false, length = 500) val message: String = "",
    @Enumerated(EnumType.STRING) @Column(nullable = false) var status: BargainStatus = BargainStatus.PENDING,
    @Column(nullable = false) val createdAt: Instant = Instant.now(),
    @Column(nullable = false) var expiresAt: Instant = Instant.now().plus(24, ChronoUnit.HOURS),
    var acceptedAt: Instant? = null,
    @Column(name = "order_id") var orderId: Long? = null
) {
    fun effectiveStatus(now: Instant = Instant.now()): BargainStatus =
        if (status in listOf(BargainStatus.PENDING, BargainStatus.ACCEPTED) && !now.isBefore(expiresAt))
            BargainStatus.EXPIRED else status
}

interface BargainRepository : JpaRepository<BargainOffer, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BargainOffer o where o.id = :id")
    fun lockById(@Param("id") id: Long): Optional<BargainOffer>
    fun findAllByBuyerIdOrSellerIdOrderByCreatedAtDesc(buyerId: String, sellerId: String): List<BargainOffer>
    fun existsByBuyerIdAndProductIdAndStatusInAndExpiresAtAfter(buyerId: String, productId: String,
        status: Collection<BargainStatus>, now: Instant): Boolean
}

data class CreateBargainRequest(val productId: String, val quantity: Double, val unitPrice: BigDecimal, val message: String = "")
data class BargainCheckoutRequest(val addressId: Long, val expectedShippingFee: Long)
data class BargainOrderResponse(val orderId: Long)
data class BargainResponse(val id: Long, val productId: String, val productName: String, val unit: String,
    val quantity: Double, val unitPrice: BigDecimal, val listedPrice: Double, val message: String,
    val buyerName: String, val sellerName: String, val isBuyer: Boolean, val status: BargainStatus,
    val createdAt: String, val expiresAt: String, val orderId: Long?)

@Service
class BargainService(private val offers: BargainRepository, private val users: UserRepository,
    private val products: ProductRepository, private val addresses: ShippingAddressRepository,
    private val delivery: DeliveryQuoteService, private val orders: OrderService,
    private val inbox: NotificationInboxRepository, private val em: EntityManager,
    private val notifications: BargainNotifications) {
    private fun fail(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
    private fun user(phone: String) = users.findByPhoneNumber(phone).orElseThrow {
        ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập") }
    private fun offer(id: Long) = offers.lockById(id).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đề nghị") }
    private fun authorize(allowed: Boolean) {
        if (!allowed) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này")
    }
    private fun response(o: BargainOffer, viewer: User) = BargainResponse(requireNotNull(o.id), requireNotNull(o.product.id),
        o.productName, o.unit, o.quantity, o.unitPrice, o.listedPrice, o.message, o.buyer.fullName,
        o.seller.fullName, o.buyer.id == viewer.id, o.effectiveStatus(), o.createdAt.toString(), o.expiresAt.toString(), o.orderId)
    private fun notify(recipient: User, o: BargainOffer, title: String) {
        val saved = inbox.save(UserNotification(recipientId = requireNotNull(recipient.id), type = "BARGAIN", status = o.status.name,
            title = title, body = "${o.productName}: ${o.quantity} ${o.unit} × ${o.unitPrice.toPlainString()}đ. Đề nghị #${o.id}."))
        notifications.send(o, recipient, saved, title)
    }
    private fun available(o: BargainOffer) {
        if (o.product.hidden || o.product.unit != o.unit || o.product.farmer.id != o.seller.id)
            fail("Sản phẩm đã ngừng bán hoặc thay đổi. Vui lòng gửi đề nghị mới.")
        if (!o.product.quantity.isFinite() || o.product.quantity < o.quantity) fail("Sản phẩm không còn đủ số lượng.")
    }
    @Transactional
    fun create(phone: String, r: CreateBargainRequest): BargainResponse {
        if (!r.quantity.isFinite() || r.quantity <= 0 || r.quantity > 1_000_000 ||
            r.unitPrice <= BigDecimal.ZERO || r.unitPrice > BigDecimal("1000000000") ||
            r.unitPrice.stripTrailingZeros().scale() > 0 || r.message.length > 500)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá phải là số đồng nguyên dương; số lượng và lời nhắn phải hợp lệ.")
        // Serialize creation per buyer to prevent duplicate active offers.
        val buyer = users.findByPhoneForAuthUpdate(phone).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED) }
        val p = products.findById(r.productId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm") }
        authorize(p.farmer.id != buyer.id)
        if (p.hidden || !p.price.isFinite() || p.price <= 0 || !p.quantity.isFinite() || p.quantity < r.quantity)
            fail("Sản phẩm không còn bán hoặc không đủ số lượng.")
        if (r.unitPrice >= BigDecimal.valueOf(p.price)) fail("Giá đề nghị phải thấp hơn giá niêm yết.")
        if (offers.existsByBuyerIdAndProductIdAndStatusInAndExpiresAtAfter(requireNotNull(buyer.id), r.productId,
                listOf(BargainStatus.PENDING, BargainStatus.ACCEPTED), Instant.now())) fail("Bạn đã có đề nghị đang hiệu lực cho sản phẩm này.")
        val o = offers.save(BargainOffer(buyer = buyer, seller = p.farmer, product = p, productName = p.name,
            unit = p.unit, quantity = r.quantity, unitPrice = r.unitPrice, listedPrice = p.price, message = r.message.trim()))
        notify(o.seller, o, "Bạn nhận được đề nghị trả giá")
        return response(o, buyer)
    }
    @Transactional(readOnly = true)
    fun list(phone: String): List<BargainResponse> {
        val viewer = user(phone)
        return offers.findAllByBuyerIdOrSellerIdOrderByCreatedAtDesc(requireNotNull(viewer.id), requireNotNull(viewer.id)).map { response(it, viewer) }
    }
    @Transactional
    fun act(phone: String, id: Long, action: String): BargainResponse {
        val viewer = user(phone)
        val o = offer(id)
        authorize(if (action == "withdraw") o.buyer.id == viewer.id else o.seller.id == viewer.id)
        if (o.effectiveStatus() != BargainStatus.PENDING) fail("Đề nghị đã được xử lý hoặc hết hạn. Vui lòng tải lại.")
        o.status = when (action) {
            "accept" -> {
                em.refresh(o.product, LockModeType.PESSIMISTIC_WRITE)
                available(o)
                o.acceptedAt = Instant.now()
                o.expiresAt = o.acceptedAt!!.plus(24, ChronoUnit.HOURS)
                BargainStatus.ACCEPTED
            }
            "reject" -> BargainStatus.REJECTED
            "withdraw" -> BargainStatus.WITHDRAWN
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Thao tác không hợp lệ")
        }
        notify(if (action == "withdraw") o.seller else o.buyer, o,
            when(action) { "accept" -> "Đề nghị đã được chấp nhận"; "reject" -> "Đề nghị đã bị từ chối"; else -> "Người mua đã rút đề nghị" })
        return response(o, viewer)
    }
    @Transactional
    fun quote(phone: String, id: Long, addressId: Long): SellerDeliveryQuote {
        val viewer = user(phone)
        val o = offer(id)
        authorize(o.buyer.id == viewer.id)
        if (o.effectiveStatus() != BargainStatus.ACCEPTED) fail("Đề nghị chưa được chấp nhận, đã dùng hoặc hết hạn.")
        available(o)
        val address = addresses.findById(addressId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ") }
        authorize(address.user.id == o.buyer.id)
        return delivery.quoteSeller(address, o.seller)
    }
    @Transactional
    fun checkout(phone: String, id: Long, r: BargainCheckoutRequest): BargainOrderResponse {
        val viewer = user(phone)
        val o = offer(id)
        authorize(o.buyer.id == viewer.id)
        o.orderId?.let { return BargainOrderResponse(it) }
        if (o.effectiveStatus() != BargainStatus.ACCEPTED) fail("Đề nghị chưa được chấp nhận hoặc đã hết hạn.")
        available(o)
        val order = orders.checkoutNegotiated(CheckoutRequest(selectedCartItemIds = emptyList(), paymentMethod = "COD",
            addressId = r.addressId, expectedShippingFee = r.expectedShippingFee), phone,
            NegotiatedPurchase(o.product, o.quantity, o.unitPrice.toDouble(), o.unit, o.seller.id))
        o.orderId = requireNotNull(order.id)
        o.status = BargainStatus.ORDERED
        return BargainOrderResponse(requireNotNull(order.id))
    }
}

@RestController
@RequestMapping("/api/bargains")
class BargainController(private val service: BargainService) {
    private fun phone() = SecurityContextHolder.getContext().authentication?.takeIf { it.isAuthenticated && it.name != "anonymousUser" }?.name
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    @GetMapping fun list() = service.list(phone())
    @PostMapping fun create(@RequestBody request: CreateBargainRequest) = service.create(phone(), request)
    @PostMapping("/{id}/accept") fun accept(@PathVariable id: Long) = service.act(phone(), id, "accept")
    @PostMapping("/{id}/reject") fun reject(@PathVariable id: Long) = service.act(phone(), id, "reject")
    @PostMapping("/{id}/withdraw") fun withdraw(@PathVariable id: Long) = service.act(phone(), id, "withdraw")
    @GetMapping("/{id}/delivery-quote") fun quote(@PathVariable id: Long, @RequestParam addressId: Long) = service.quote(phone(), id, addressId)
    @PostMapping("/{id}/checkout") fun checkout(@PathVariable id: Long, @RequestBody request: BargainCheckoutRequest) = service.checkout(phone(), id, request)
}
