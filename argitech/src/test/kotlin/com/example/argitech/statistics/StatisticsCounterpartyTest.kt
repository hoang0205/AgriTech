package com.uet.agritech.statistics

import com.uet.agritech.order.*
import com.uet.agritech.product.Product
import com.uet.agritech.user.*
import io.mockk.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.*

class StatisticsCounterpartyTest {
    @Test fun `spending shows seller while revenue shows buyer`() {
        val users = mockk<UserRepository>()
        val repository = mockk<StatisticsOverviewRepository>()
        val buyer = mockk<User>(relaxed = true)
        val seller = mockk<User>(relaxed = true)
        every { buyer.id } returns "buyer"
        every { buyer.fullName } returns "Buyer"
        every { buyer.avatarUrl } returns "buyer.png"
        every { seller.id } returns "seller"
        every { seller.fullName } returns "Seller"
        every { seller.avatarUrl } returns "seller.png"
        every { seller.phoneNumber } returns "seller-phone"
        val order = mockk<Order>(relaxed = true)
        every { order.id } returns 1L
        every { order.user } returns buyer
        every { order.orderDate } returns LocalDateTime.of(2026, 10, 23, 10, 0)
        every { order.phoneNumber } returns "buyer-phone"
        every { order.totalAmount } returns 125000.0
        val product = mockk<Product>(relaxed = true)
        every { product.id } returns "product"
        every { product.farmer } returns seller
        every { product.category } returns "Fruit"
        val item = mockk<OrderItem>(relaxed = true)
        every { item.order } returns order
        every { item.product } returns product
        every { item.price } returns 100000.0
        every { item.quantity } returns 1.0
        every { item.costPrice } returns 70000.0
        every { users.findByPhoneNumber("viewer") } returns Optional.of(buyer)
        every { repository.findBuyerItems(buyer, any(), any()) } returns listOf(item)
        every { repository.findSellerItems(buyer, any(), any()) } returns listOf(item)
        every { repository.findSellerFinanceItems(buyer, any(), any()) } returns emptyList()
        val result = StatisticsOverviewService(users, repository).getOverview("viewer", 2026, 10)
        val spending = result.spending.orders.single()
        assertEquals("Seller", spending.sellerName)
        assertEquals("seller.png", spending.sellerAvatar)
        assertEquals("seller-phone", spending.sellerPhone)
        assertNull(spending.buyerName)
        assertNull(spending.buyerPhone)
        assertEquals(125000.0, spending.amount)
        val revenue = result.revenue!!.orders.single()
        assertEquals("Buyer", revenue.buyerName)
        assertEquals("buyer-phone", revenue.buyerPhone)
        assertNull(revenue.sellerName)
        assertEquals(100000.0, revenue.amount)
    }
}
