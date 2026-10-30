package com.uet.agritech.order

import com.uet.agritech.product.Product
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.*

class DeliveryManagementServiceTest {
    private val orders = mockk<OrderRepository>()
    private val items = mockk<OrderItemRepository>()
    private val service = DeliveryManagementService(orders, items)
    private lateinit var order: Order

    @BeforeEach
    fun setup() {
        val user = User(id = "seller", fullName = "Seller", phoneNumber = "0900000002", email = "s@test.vn", password = "x", role = Role.FARMER)
        val product = Product(id = "p", name = "Rau", category = "Rau", price = 200000.0, quantity = 10.0, unit = "kg", description = "Rau", farmer = user)
        order = Order(id = 1, user = user, totalAmount = 225000.0, shippingAddress = "Address", phoneNumber = "0900000001", status = "CONFIRMED", shippingFee = 25000)
        every { orders.findForStatusUpdate(1) } returns Optional.of(order)
        every { orders.save(any()) } answers { firstArg() }
        every { items.findAllByOrder(order) } returns listOf(OrderItem(order = order, product = product, quantity = 1.0, price = 200000.0))
    }

    @Test
    fun `unpaid order cannot choose delivery or write changes`() {
        order.status = "UNPAID"
        assertFailsWith<ResponseStatusException> { service.choose(1, "0900000002", DeliveryMethod.SELF) }
        assertNull(order.deliveryMethod)
        verify(exactly = 0) { orders.save(any()) }
    }

    @Test
    fun `seller chooses self delivery and explicitly records zero or unknown cost`() {
        service.choose(1, "0900000002", DeliveryMethod.SELF)
        assertEquals(DeliveryMethod.SELF, order.deliveryMethod)
        assertNull(order.actualShippingCost)
        service.cost(1, "0900000002", 0)
        assertEquals(0L, order.actualShippingCost)
        service.cost(1, "0900000002", null)
        assertNull(order.actualShippingCost)
        assertEquals(25000L, order.shippingFee)
    }

    @Test
    fun `active or uncertain GHN intent cannot switch to self delivery`() {
        order.ghnRequestJson = "{}"; order.ghnStatus = "creating"
        assertFailsWith<IllegalArgumentException> { service.choose(1, "0900000002", DeliveryMethod.SELF) }
        order.ghnStatus = "cancel"
        service.choose(1, "0900000002", DeliveryMethod.SELF)
        assertEquals(DeliveryMethod.SELF, order.deliveryMethod)
    }

    @Test
    fun `switching method preserves incurred expenses instead of erasing them`() {
        order.actualShippingCost = 5000
        service.choose(1, "0900000002", DeliveryMethod.GHN)
        service.choose(1, "0900000002", DeliveryMethod.SELF)
        assertEquals(5000L, order.actualShippingCost)
        assertEquals(225000.0, order.totalAmount)
    }

    @Test
    fun `completed and cancelled orders can record expenses but cannot change delivery method`() {
        for (status in listOf("COMPLETED", "CANCELLED")) {
            order.status = status
            service.cost(1, "0900000002", 30000)
            assertEquals(30000L, order.actualShippingCost)
            assertFailsWith<IllegalArgumentException> { service.choose(1, "0900000002", DeliveryMethod.SELF) }
            assertEquals(status, order.status)
        }
    }

    @Test
    fun `quote is not automatically actual cost and invalid values are rejected`() {
        order.ghnShippingFee = 30000
        assertNull(service.view(1, "0900000002").actualCost)
        assertFailsWith<IllegalArgumentException> { service.cost(1, "0900000002", -1) }
        assertFailsWith<IllegalArgumentException> { service.cost(1, "0900000002", 100_000_001) }
        assertNull(order.actualShippingCost)
    }

    @Test
    fun `another user cannot read or modify seller expenses`() {
        assertFailsWith<ResponseStatusException> { service.view(1, "stranger") }
        assertFailsWith<ResponseStatusException> { service.choose(1, "stranger", DeliveryMethod.GHN) }
        assertFailsWith<ResponseStatusException> { service.cost(1, "stranger", 0) }
        verify(exactly = 0) { orders.save(any()) }
    }
}
