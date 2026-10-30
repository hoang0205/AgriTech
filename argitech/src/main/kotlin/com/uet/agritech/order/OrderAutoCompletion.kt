package com.uet.agritech.order

import com.uet.agritech.notification.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class OrderAutoCompletionService(
    private val orders: OrderRepository,
    private val items: OrderItemRepository,
    private val notifications: NotificationService
) {
    @Transactional
    fun completeIfDue(id: Long, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val order = orders.findForStatusUpdate(id).orElse(null) ?: return false
        val deadline = order.completionDeadline() ?: return false
        if (order.status != "SHIPPING" || order.sellerDeliveredAt == null || order.complaintOpen ||
            order.isAwaitingOnlinePayment() || deadline > now) return false
        order.markCompleted("AUTO", now)
        orders.save(order)
        notifications.sendOrderStatusNotification(order.user, id, "COMPLETED", "Đơn hàng đã tự hoàn thành",
            "Đơn #$id đã hoàn thành sau 48 giờ kể từ xác nhận giao hàng, không có khiếu nại đang mở.")
        items.findAllByOrder(order).map { it.product.farmer }.distinctBy { it.id }.forEach {
            notifications.sendOrderStatusNotification(it, id, "RECEIVED_BY_BUYER", "Đơn hàng đã tự hoàn thành",
                "Đơn #$id đã hoàn thành sau thời gian chờ xác nhận của người mua.")
        }
        return true
    }
}

@Component
class OrderAutoCompletionJob(private val orders: OrderRepository, private val completion: OrderAutoCompletionService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    fun run() {
        val now = LocalDateTime.now()
        // Each order is checked again under its own lock and transaction.
        orders.findDueCompletionIds(now, now.minusHours(48), PageRequest.of(0, 100)).forEach { id ->
            try { completion.completeIfDue(id, now) }
            catch (e: Exception) { logger.error("Unable to auto-complete order {}", id, e) }
        }
    }
}
