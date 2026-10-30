package com.uet.agritech.order

import com.uet.agritech.product.Product
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.*

class OrderDetailServiceTest {
    private val orders = mockk<OrderRepository>()
    private val items = mockk<OrderItemRepository>()
    private val service = OrderDetailService(orders, items)
    private val buyer = user("buyer", "0900000001", Role.BUYER)
    private val seller = user("seller", "0900000002", Role.FARMER)
    private val order = Order(id = 42L, user = buyer, totalAmount = 35000.0,
        shippingAddress = "Địa chỉ đã đặt", phoneNumber = "0900000099", receiverName = "Người nhận khác",
        shippingFee = 5000L, paymentMethod = "COD", deliveryMethod = DeliveryMethod.SELF,
        actualShippingCost = 8000L)
    private val product = Product(id = "product", name = "Rau", category = "Rau", price = 99000.0,
        quantity = 10.0, unit = "kg", description = "", farmer = seller)
    private val line = OrderItem(id = 1L, order = order, product = product, quantity = 2.0, price = 15000.0)

    init {
        every { orders.findById(42L) } returns Optional.of(order)
        every { items.findAllByOrder(order) } returns listOf(line)
    }

    @Test
    fun `buyer sees ordered prices and receiver rather than current product price or account phone`() {
        val result = service.get(42L, buyer.phoneNumber)
        assertEquals("BUYER", result.viewerRole)
        assertEquals(15000.0, result.items.single().unitPrice)
        assertEquals(30000.0, result.subtotal)
        assertEquals(35000.0, result.totalAmount)
        assertEquals("Người nhận khác", result.receiverName)
        assertEquals("0900000099", result.receiverPhone)
        assertNull(result.actualShippingCost)
        assertTrue(result.canCancel)
        assertNull(result.nextStatus)
    }

    @Test
    fun `owning seller can view costs and confirm pending order`() {
        val result = service.get(42L, seller.phoneNumber)
        assertEquals("SELLER", result.viewerRole)
        assertEquals(8000L, result.actualShippingCost)
        assertEquals("CONFIRMED", result.nextStatus)
        assertTrue(result.canCancel)
        assertFalse(result.canPay)
    }

    @Test
    fun `unrelated account cannot read order`() {
        val error = assertFailsWith<ResponseStatusException> { service.get(42L, "0900000003") }
        assertEquals(HttpStatus.FORBIDDEN, error.statusCode)
    }

    @Test
    fun `seller cannot read legacy order containing another shops items`() {
        val other = user("other", "0900000004", Role.FARMER)
        val otherProduct = Product(id = "other-product", name = "Cam", category = "Quả", price = 1.0,
            quantity = 1.0, unit = "kg", description = "", farmer = other)
        every { items.findAllByOrder(order) } returns listOf(line,
            OrderItem(id = 2L, order = order, product = otherProduct, quantity = 1.0, price = 1.0))
        assertFailsWith<ResponseStatusException> { service.get(42L, seller.phoneNumber) }
        assertEquals(2, service.get(42L, buyer.phoneNumber).sellers.size)
    }

    @Test
    fun `missing order returns not found`() {
        every { orders.findById(99L) } returns Optional.empty()
        assertEquals(HttpStatus.NOT_FOUND,
            assertFailsWith<ResponseStatusException> { service.get(99L, buyer.phoneNumber) }.statusCode)
    }

    @Test
    fun `unpaid order cannot be advanced by seller`() {
        order.status = "UNPAID"
        assertNull(service.get(42L, seller.phoneNumber).nextStatus)
        assertFalse(service.get(42L, seller.phoneNumber).canCancel)
    }

    @Test
    fun `active shipment prevents manual cancellation and transitions`() {
        order.status = "SHIPPING"
        order.ghnRequestJson = "{}"
        order.ghnStatus = "delivering"
        assertEquals("COMPLETED", service.get(42L, seller.phoneNumber).nextStatus)
        assertFalse(service.get(42L, seller.phoneNumber).canCancel)
        assertFalse(service.get(42L, buyer.phoneNumber).canCancel)
    }

    @Test
    fun `completed and cancelled orders do not offer processing actions`() {
        for (status in listOf("COMPLETED", "CANCELLED")) {
            order.status = status
            val result = service.get(42L, seller.phoneNumber)
            assertNull(result.nextStatus)
            assertFalse(result.canCancel)
            assertFalse(result.canPay)
        }
    }

    @Test
    fun `only buyer can resume payment for an unpaid VNPay order`() {
        val unpaid = Order(id = 42L, user = buyer, totalAmount = 35000.0,
            shippingAddress = "Địa chỉ", phoneNumber = buyer.phoneNumber, status = "UNPAID", paymentMethod = "VNPAY")
        every { orders.findById(42L) } returns Optional.of(unpaid)
        every { items.findAllByOrder(unpaid) } returns listOf(line)
        assertTrue(service.get(42L, buyer.phoneNumber).canPay)
        assertFalse(service.get(42L, seller.phoneNumber).canPay)
        unpaid.status = "PENDING"
        unpaid.vnpayTransactionNo = "123456"
        assertFalse(service.get(42L, buyer.phoneNumber).canPay)
        assertEquals("PAID", service.get(42L, buyer.phoneNumber).paymentStatus)
    }

    @Test
    fun `buyer can confirm receipt only while shipping even with an active carrier shipment`() {
        order.sellerDeliveredAt = java.time.LocalDateTime.now()
        order.ghnRequestJson = "{}"
        order.ghnStatus = "delivering"
        for (status in listOf("UNPAID", "PENDING", "CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED")) {
            order.status = status
            assertEquals(if (status == "SHIPPING") "COMPLETED" else null,
                service.get(42L, buyer.phoneNumber).nextStatus)
        }
    }

    @Test
    fun `COD completion does not claim a verified online payment`() {
        order.status = "COMPLETED"
        assertEquals("COD", service.get(42L, buyer.phoneNumber).paymentStatus)
    }

    @Test
    fun `each party sees confirmation action only until their own confirmation`() {
        order.status = "SHIPPING"
        order.buyerReceivedAt = java.time.LocalDateTime.now()
        assertNull(service.get(42L, buyer.phoneNumber).nextStatus)
        assertEquals("COMPLETED", service.get(42L, seller.phoneNumber).nextStatus)
        assertNotNull(service.get(42L, seller.phoneNumber).buyerReceivedAt)
        order.buyerReceivedAt = null
        order.sellerDeliveredAt = java.time.LocalDateTime.now()
        assertNull(service.get(42L, seller.phoneNumber).nextStatus)
        assertEquals("COMPLETED", service.get(42L, buyer.phoneNumber).nextStatus)
    }

    @Test
    fun `unverified VNPay order never offers fulfillment actions even with incorrect persisted status`() {
        val online = Order(id = 42L, user = buyer, totalAmount = 35000.0, shippingAddress = "Address",
            phoneNumber = buyer.phoneNumber, paymentMethod = "VNPAY", deliveryMethod = DeliveryMethod.SELF)
        every { orders.findById(42L) } returns Optional.of(online)
        every { items.findAllByOrder(online) } returns listOf(line)
        for (status in listOf("UNPAID", "PENDING", "CONFIRMED", "SHIPPING")) {
            online.status = status
            val result = service.get(42L, seller.phoneNumber)
            assertNull(result.nextStatus)
            assertFalse(result.canCancel)
            assertFalse(result.canManageDelivery)
            assertNull(service.get(42L, buyer.phoneNumber).nextStatus)
        }
    }

    private fun user(id: String, phone: String, role: Role) = User(
        id = id, fullName = id, phoneNumber = phone, email = "$id@example.test", password = "encoded", role = role)
}
