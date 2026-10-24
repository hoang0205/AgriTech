package com.uet.agritech.bargain

import com.uet.agritech.delivery.DeliveryQuoteService
import com.uet.agritech.notification.NotificationInboxRepository
import com.uet.agritech.order.*
import com.uet.agritech.product.*
import com.uet.agritech.user.*
import io.mockk.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import kotlin.test.*

class BargainServiceTest {
    private val offers = mockk<BargainRepository>()
    private val users = mockk<UserRepository>()
    private val products = mockk<ProductRepository>()
    private val addresses = mockk<ShippingAddressRepository>()
    private val delivery = mockk<DeliveryQuoteService>()
    private val orders = mockk<OrderService>()
    private val inbox = mockk<NotificationInboxRepository>(relaxed = true)
    private val em = mockk<EntityManager>(relaxUnitFun = true)
    private val service = BargainService(offers, users, products, addresses, delivery, orders, inbox, em,
        mockk(relaxUnitFun = true))
    private val buyer = mockk<User> { every { id } returns "buyer"; every { fullName } returns "Buyer" }
    private val seller = mockk<User> { every { id } returns "seller"; every { fullName } returns "Seller" }
    private val product = Product(id = "p", name = "Tomato", category = "Vegetable", price = 30000.0,
        quantity = 30.0, unit = "kg", description = "", farmer = seller)
    private fun offer(status: BargainStatus = BargainStatus.ACCEPTED) = BargainOffer(id = 1L, buyer = buyer,
        seller = seller, product = product, productName = product.name, unit = "kg", quantity = 20.0,
        unitPrice = BigDecimal("25000"), listedPrice = 30000.0, status = status)
    private fun setup(o: BargainOffer, viewer: User = buyer) {
        every { inbox.save(any<com.uet.agritech.notification.UserNotification>()) } answers { firstArg() }
        every { users.findByPhoneNumber("phone") } returns Optional.of(viewer)
        every { offers.lockById(1L) } returns Optional.of(o)
    }
    @Test fun `checkout uses agreed price and retry returns same order`() {
        val o = offer(); setup(o)
        product.price = 50000.0
        every { orders.checkoutNegotiated(any(), "phone", any()) } returns mockk { every { id } returns 9L }
        assertEquals(9L, service.checkout("phone", 1, BargainCheckoutRequest(2, 15000)).orderId)
        assertEquals(BargainStatus.ORDERED, o.status)
        assertEquals(9L, service.checkout("phone", 1, BargainCheckoutRequest(2, 15000)).orderId)
        verify(exactly = 1) { orders.checkoutNegotiated(match { it.paymentMethod == "COD" }, "phone",
            match { it.unitPrice == 25000.0 && it.quantity == 20.0 }) }
    }
    @Test fun `expired accepted offer cannot create order`() {
        val o = offer(); o.expiresAt = Instant.now().minusSeconds(1); setup(o)
        assertFailsWith<ResponseStatusException> { service.checkout("phone", 1, BargainCheckoutRequest(2, 15000)) }
        verify { orders wasNot Called }
        assertEquals(BargainStatus.EXPIRED, o.effectiveStatus())
    }
    @Test fun `seller cannot checkout buyer offer`() {
        setup(offer(), seller)
        assertEquals(403, assertFailsWith<ResponseStatusException> {
            service.checkout("phone", 1, BargainCheckoutRequest(2, 15000)) }.statusCode.value())
        verify { orders wasNot Called }
    }
    @Test fun `insufficient stock and changed unit block checkout`() {
        setup(offer()); product.quantity = 10.0
        assertFailsWith<ResponseStatusException> { service.checkout("phone", 1, BargainCheckoutRequest(2, 15000)) }
        product.quantity = 30.0; product.unit = "box"
        assertFailsWith<ResponseStatusException> { service.checkout("phone", 1, BargainCheckoutRequest(2, 15000)) }
        verify { orders wasNot Called }
    }
    @Test fun `accept gives new checkout deadline without reserving stock`() {
        val o = offer(BargainStatus.PENDING); setup(o, seller)
        val result = service.act("phone", 1, "accept")
        assertEquals(BargainStatus.ACCEPTED, result.status)
        assertEquals(30.0, product.quantity)
        assertTrue(o.expiresAt.isAfter(Instant.now().plusSeconds(86000)))
    }
    @Test fun `withdrawn offer cannot be accepted`() {
        setup(offer(BargainStatus.WITHDRAWN), seller)
        assertFailsWith<ResponseStatusException> { service.act("phone", 1, "accept") }
    }
    @Test fun `duplicate active offer rejected`() {
        every { users.findByPhoneForAuthUpdate("phone") } returns Optional.of(buyer)
        every { products.findById("p") } returns Optional.of(product)
        every { offers.existsByBuyerIdAndProductIdAndStatusInAndExpiresAtAfter(any(), any(), any(), any()) } returns true
        assertFailsWith<ResponseStatusException> { service.create("phone", CreateBargainRequest("p", 20.0, BigDecimal("25000"))) }
        verify(exactly = 0) { offers.save(any()) }
    }
}
