package com.uet.agritech.notification

import com.uet.agritech.user.UserRepository
import jakarta.persistence.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Entity
@Table(name = "user_notifications", indexes = [Index(name = "idx_notification_recipient_read", columnList = "recipient_id,read_at")])
class UserNotification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "recipient_id", nullable = false)
    val recipientId: String,
    @Column(name = "order_id", nullable = true)
    val orderId: Long? = null,
    @Column(nullable = false)
    val status: String,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val body: String,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "read_at")
    var readAt: Instant? = null,
    @Column(nullable = false, columnDefinition = "varchar(32) default 'ORDER_STATUS'")
    val type: String = "ORDER_STATUS",
    @Column
    val reportId: Long? = null
)

interface NotificationInboxRepository : JpaRepository<UserNotification, Long> {
    fun findAllByRecipientIdOrderByCreatedAtDescIdDesc(recipientId: String, pageable: Pageable): Page<UserNotification>
    fun countByRecipientIdAndReadAtIsNull(recipientId: String): Long

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserNotification n SET n.readAt = :now WHERE n.recipientId = :recipientId AND n.id <= :upToId AND n.readAt IS NULL")
    fun markAllRead(@Param("recipientId") recipientId: String, @Param("upToId") upToId: Long, @Param("now") now: Instant): Int
}

data class InboxNotificationResponse(
    val id: Long,
    val title: String,
    val body: String,
    val orderId: Long?,
    val status: String,
    val targetScreen: String,
    val createdAt: String,
    val read: Boolean,
    val type: String = "ORDER_STATUS",
    val reportId: Long? = null
)

data class NotificationInboxResponse(val items: List<InboxNotificationResponse>, val hasMore: Boolean, val unreadCount: Long)
data class UnreadNotificationCount(val count: Long)
data class ReadAllNotificationsRequest(val upToId: Long)

@Service
class NotificationInboxService(private val inbox: NotificationInboxRepository, private val users: UserRepository) {
    @Transactional
    fun recordReport(recipientId: String, reportId: Long, status: String, title: String, body: String): UserNotification {
        return inbox.save(UserNotification(recipientId = recipientId, reportId = reportId,
            type = "REPORT_STATUS", status = status, title = title, body = body))
    }

    @Transactional
    fun record(recipientId: String, orderId: Long, status: String, title: String, body: String): UserNotification {
        return inbox.save(UserNotification(recipientId = recipientId, orderId = orderId, status = status, title = title, body = body))
    }

    @Transactional(readOnly = true)
    fun list(phone: String, page: Int, size: Int): NotificationInboxResponse {
        require(page >= 0 && size in 1..100) { "Thông tin phân trang không hợp lệ" }
        val userId = userId(phone)
        val result = inbox.findAllByRecipientIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size))
        return NotificationInboxResponse(result.content.map { item ->
            InboxNotificationResponse(requireNotNull(item.id), item.title, item.body, item.orderId, item.status,
                when {
                    item.type == "BARGAIN" -> "bargains"
                    item.type == "REPORT_STATUS" -> "my_reports"
                    item.status == "NEW_ORDER_SELLER" -> "seller_orders"
                    else -> "buyer_orders"
                }, item.createdAt.toString(), item.readAt != null, item.type, item.reportId)
        }, result.hasNext(), inbox.countByRecipientIdAndReadAtIsNull(userId))
    }

    @Transactional(readOnly = true)
    fun unread(phone: String) = UnreadNotificationCount(inbox.countByRecipientIdAndReadAtIsNull(userId(phone)))

    @Transactional
    fun read(phone: String, id: Long): UnreadNotificationCount {
        val userId = userId(phone)
        val item = inbox.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Thông báo không tồn tại") }
        if (item.recipientId != userId) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Thông báo không tồn tại")
        if (item.readAt == null) {
            item.readAt = Instant.now()
            inbox.saveAndFlush(item)
        }
        return UnreadNotificationCount(inbox.countByRecipientIdAndReadAtIsNull(userId))
    }

    @Transactional
    fun readAll(phone: String, upToId: Long): UnreadNotificationCount {
        require(upToId > 0) { "Mốc thông báo không hợp lệ" }
        val userId = userId(phone)
        inbox.markAllRead(userId, upToId, Instant.now())
        return UnreadNotificationCount(inbox.countByRecipientIdAndReadAtIsNull(userId))
    }

    private fun userId(phone: String) = users.findByPhoneNumber(phone).orElseThrow {
        ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập")
    }.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không hợp lệ")
}

@RestController
@RequestMapping("/api/notifications")
class NotificationInboxController(private val service: NotificationInboxService) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int) = service.list(phone(), page, size)

    @GetMapping("/unread-count")
    fun unread() = service.unread(phone())

    @PostMapping("/{id}/read")
    fun read(@PathVariable id: Long) = service.read(phone(), id)

    @PostMapping("/read-all")
    fun readAll(@RequestBody request: ReadAllNotificationsRequest) = service.readAll(phone(), request.upToId)

    private fun phone(): String {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || !auth.isAuthenticated || auth.name == "anonymousUser") {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập")
        }
        return auth.name
    }
}
