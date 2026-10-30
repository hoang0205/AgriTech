package com.uet.agritech.order

import com.uet.agritech.notification.NotificationService
import com.uet.agritech.product.Product
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import io.mockk.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

private class WorkflowFixture {
    val now: LocalDateTime = LocalDateTime.of(2026, 10, 25, 10, 0)
    val buyer = User(id = "buyer", fullName = "Buyer", phoneNumber = "buyer-phone", email = "b@test.vn", password = "x", role = Role.BUYER)
    val seller = User(id = "seller", fullName = "Seller", phoneNumber = "seller-phone", email = "s@test.vn", password = "x", role = Role.FARMER)
    val order = Order(id = 1L, user = buyer, totalAmount = 50000.0, shippingAddress = "Address", phoneNumber = "receiver",
        status = "SHIPPING", paymentMethod = "COD", sellerDeliveredAt = now.minusHours(48))
    val orders = mockk<OrderRepository>()
    val items = mockk<OrderItemRepository>()
    val notifications = mockk<NotificationService>(relaxUnitFun = true)
    val complaints = mockk<OrderComplaintRepository>()
    val em = mockk<EntityManager>(relaxUnitFun = true)
    var current: OrderComplaint? = null
    val auto = OrderAutoCompletionService(orders, items, notifications)
    val service = OrderComplaintService(complaints, orders, items, notifications, em)
    val request = CreateOrderComplaintRequest(OrderComplaintReason.NOT_RECEIVED, "Chưa nhận được đơn hàng đã đặt.")

    init {
        val product = Product(id = "p", name = "Rau", category = "Rau", price = 50000.0, quantity = 10.0, unit = "kg", description = "", farmer = seller)
        every { orders.findForStatusUpdate(1L) } returns Optional.of(order)
        every { orders.findById(1L) } returns Optional.of(order)
        every { orders.save(any<Order>()) } answers { firstArg() }
        every { items.findAllByOrder(order) } returns listOf(OrderItem(id = 1L, order = order, product = product, quantity = 1.0, price = 50000.0))
        every { complaints.findByOrderId(1L) } answers { current }
        every { complaints.findForOrderUpdate(1L) } answers { current }
        every { complaints.findById(7L) } answers { Optional.ofNullable(current) }
        every { complaints.save(any<OrderComplaint>()) } answers {
            val input = firstArg<OrderComplaint>()
            val saved = if (input.id == null) OrderComplaint(id = 7L, orderId = input.orderId,
                reporterId = input.reporterId, reporterName = input.reporterName, reason = input.reason,
                description = input.description, imageUrls = input.imageUrls, events = input.events) else input
            current = saved
            saved
        }
    }
}

class OrderAutoCompletionTest {
    @Test fun `not before 48 hours and exactly once at the deadline`() {
        val f = WorkflowFixture()
        assertFalse(f.auto.completeIfDue(1L, f.now.minusNanos(1)))
        assertEquals("SHIPPING", f.order.status)
        assertTrue(f.auto.completeIfDue(1L, f.now))
        assertFalse(f.auto.completeIfDue(1L, f.now.plusMinutes(1)))
        assertEquals("COMPLETED", f.order.status)
        assertEquals("AUTO", f.order.completionMethod)
        assertEquals(f.now, f.order.completedAt)
        assertNull(f.order.buyerReceivedAt)
        verify(exactly = 1) { f.orders.save(f.order) }
        verify(exactly = 2) { f.notifications.sendOrderStatusNotification(any<User>(), 1L, any(), any(), any()) }
    }

    @Test fun `no delivery report or open complaint prevents automatic completion`() {
        val f = WorkflowFixture()
        f.order.sellerDeliveredAt = null
        f.order.autoCompleteAt = f.now.minusHours(1)
        assertFalse(f.auto.completeIfDue(1L, f.now))
        f.order.sellerDeliveredAt = f.now.minusDays(3)
        f.order.complaintOpen = true
        assertFalse(f.auto.completeIfDue(1L, f.now))
        f.order.complaintOpen = false
        f.order.autoCompleteAt = f.now.plusHours(48)
        assertFalse(f.auto.completeIfDue(1L, f.now))
        verify(exactly = 0) { f.orders.save(any<Order>()) }
    }

    @Test fun `cancelled and already completed purchases never reopen`() {
        val f = WorkflowFixture()
        for (status in listOf("CANCELLED", "COMPLETED", "PENDING", "UNPAID")) {
            f.order.status = status
            assertFalse(f.auto.completeIfDue(1L, f.now))
            assertEquals(status, f.order.status)
        }
        verify(exactly = 0) { f.orders.save(any<Order>()) }
    }
}

class OrderComplaintWorkflowTest {
    @Test fun `complaint pauses auto completion and is visible to buyer and owning seller`() {
        val f = WorkflowFixture()
        val result = f.service.create(1L, f.buyer.phoneNumber, f.request)
        assertEquals(7L, result.id)
        assertTrue(f.order.complaintOpen)
        assertEquals(7L, f.order.complaintId)
        assertFalse(f.auto.completeIfDue(1L, f.now.plusDays(1)))
        assertEquals(7L, f.service.context(1L, f.seller.phoneNumber).complaint?.id)
        assertFalse(f.service.context(1L, f.buyer.phoneNumber).canCreate)
        assertFailsWith<ResponseStatusException> { f.service.create(1L, f.buyer.phoneNumber, f.request) }
    }

    @Test fun `unrelated accounts cannot view complaint and seller cannot create one for buyer`() {
        val f = WorkflowFixture()
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            f.service.context(1L, "stranger")
        }.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            f.service.create(1L, f.seller.phoneNumber, f.request)
        }.statusCode)
        verify(exactly = 0) { f.complaints.save(any<OrderComplaint>()) }
    }

    @Test fun `review keeps pause and closing gives a fresh 48 hour response window`() {
        val f = WorkflowFixture()
        f.service.create(1L, f.buyer.phoneNumber, f.request)
        f.service.update(7L, "admin", UpdateOrderComplaintRequest(OrderComplaintStatus.REVIEWING, "Đang đối chiếu bằng chứng."))
        assertTrue(f.order.complaintOpen)
        val before = LocalDateTime.now()
        val result = f.service.update(7L, "admin", UpdateOrderComplaintRequest(OrderComplaintStatus.RESOLVED, "Đã xác minh và giữ nguyên đơn hàng."))
        assertFalse(f.order.complaintOpen)
        assertEquals("SHIPPING", f.order.status)
        assertTrue(requireNotNull(f.order.autoCompleteAt) >= before.plusHours(48))
        assertFalse(f.auto.completeIfDue(1L, before.plusHours(47)))
        assertEquals(3, result.events.size)
        assertEquals("admin", f.current?.handledBy)
        assertFailsWith<ResponseStatusException> {
            f.service.update(7L, "admin", UpdateOrderComplaintRequest(OrderComplaintStatus.REJECTED, "Đóng lần hai."))
        }
    }

    @Test fun `admin can complete delivered purchase without forging buyer receipt`() {
        val f = WorkflowFixture()
        f.service.create(1L, f.buyer.phoneNumber, f.request)
        f.service.update(7L, "admin", UpdateOrderComplaintRequest(OrderComplaintStatus.RESOLVED,
            "Đã đối chiếu bằng chứng giao hàng.", OrderComplaintOutcome.COMPLETE_ORDER))
        assertEquals("COMPLETED", f.order.status)
        assertEquals("ADMIN", f.order.completionMethod)
        assertNull(f.order.buyerReceivedAt)
        assertFalse(f.order.complaintOpen)
    }

    @Test fun `admin can close a disputed shipment without assuming returned stock or buyer receipt`() {
        val f = WorkflowFixture()
        f.service.create(1L, f.buyer.phoneNumber, f.request)
        val result = f.service.update(7L, "admin", UpdateOrderComplaintRequest(OrderComplaintStatus.RESOLVED,
            "Đã đối chiếu và đóng đơn giao không thành công.", OrderComplaintOutcome.CANCEL_ORDER))
        assertEquals("CANCELLED", f.order.status)
        assertEquals("CANCEL_ORDER", result.outcome)
        assertNull(f.order.buyerReceivedAt)
        assertFalse(f.order.complaintOpen)
        assertFalse(f.auto.completeIfDue(1L, f.now.plusDays(3)))
    }

    @Test fun `admin cannot complete when seller has not reported delivery`() {
        val f = WorkflowFixture()
        f.order.sellerDeliveredAt = null
        f.service.create(1L, f.buyer.phoneNumber, f.request)
        assertFailsWith<IllegalArgumentException> {
            f.service.update(7L, "admin", UpdateOrderComplaintRequest(OrderComplaintStatus.RESOLVED,
                "Chưa có xác nhận giao hàng.", OrderComplaintOutcome.COMPLETE_ORDER))
        }
        assertEquals("SHIPPING", f.order.status)
        assertTrue(f.order.complaintOpen)
    }

    @Test fun `complaint eligibility rejects old completed and unshipped orders`() {
        val f = WorkflowFixture()
        for (status in listOf("PENDING", "CONFIRMED", "UNPAID", "CANCELLED")) {
            f.order.status = status
            assertFailsWith<ResponseStatusException> { f.service.create(1L, f.buyer.phoneNumber, f.request) }
        }
        f.order.status = "COMPLETED"
        f.order.completedAt = LocalDateTime.now().minusHours(49)
        assertFailsWith<ResponseStatusException> { f.service.create(1L, f.buyer.phoneNumber, f.request) }
        f.order.completedAt = LocalDateTime.now().minusHours(1)
        assertEquals(7L, f.service.create(1L, f.buyer.phoneNumber, f.request).id)
    }

    @Test fun `invalid descriptions or image links never pause an order`() {
        val f = WorkflowFixture()
        assertFailsWith<IllegalArgumentException> { f.service.create(1L, f.buyer.phoneNumber, f.request.copy(description = "x")) }
        assertFailsWith<IllegalArgumentException> { f.service.create(1L, f.buyer.phoneNumber, f.request.copy(imageUrls = listOf("javascript:alert(1)"))) }
        assertFalse(f.order.complaintOpen)
        verify(exactly = 0) { f.orders.save(any<Order>()) }
    }
}
