package com.uet.agritech.statistics

import com.uet.agritech.order.*
import com.uet.agritech.product.Product
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.*

class SellerFinanceCalculatorTest {
    @Test
    fun `64900 carrier expense deducts 39900 shipping loss from goods profit`() {
        val item = row(expense = 64900)
        item.order.ghnShippingFee = 64900
        val result = SellerFinanceCalculator.calculate(listOf(item), null)
        assertEquals(200000.0, result.merchandiseRevenue)
        assertEquals(60000.0, result.grossProfit)
        assertEquals(-39900L, result.shippingMargin)
        assertEquals(20100.0, result.profitAfterShipping)
        item.order.actualShippingCost = null
        assertEquals(60000.0, SellerFinanceCalculator.calculate(listOf(item), null).profitAfterShipping)
    }

    private val seller = User(id = "s", fullName = "S", phoneNumber = "0900000002", email = "s@test.vn", password = "x", role = Role.FARMER)
    private val product = Product(id = "p", name = "Rau", category = "Rau", price = 999999.0, quantity = 10.0, unit = "kg", description = "Rau", farmer = seller)

    private fun row(id: Long = 1, expense: Long? = 30000, cost: Double? = 140000.0,
        status: String = "COMPLETED", month: Int = 10, method: DeliveryMethod = DeliveryMethod.GHN): OrderItem {
        val order = Order(id = id, user = seller, totalAmount = 225000.0, shippingFee = 25000,
            shippingAddress = "Address", phoneNumber = "0900000001", status = status,
            orderDate = LocalDateTime.of(2026, month, 10, 12, 0), deliveryMethod = method,
            actualShippingCost = expense, ghnShippingFee = 30000)
        return OrderItem(order = order, product = product, quantity = 1.0, price = 200000.0, costPrice = cost)
    }

    @Test
    fun `GHN example uses snapshot merchandise amounts and gives 55000 profit`() {
        val result = SellerFinanceCalculator.calculate(listOf(row()), null)
        assertEquals(200000.0, result.merchandiseRevenue)
        assertEquals(140000.0, result.costOfGoods)
        assertEquals(60000.0, result.grossProfit)
        assertEquals(-5000L, result.shippingMargin)
        assertEquals(55000.0, result.profitAfterShipping)
    }

    @Test
    fun `self shipping examples give 65000 and 75000 without changing customer shipping`() {
        for ((cost, expected) in listOf(20000L to 65000.0, 10000L to 75000.0)) {
            val result = SellerFinanceCalculator.calculate(listOf(row(expense = cost, method = DeliveryMethod.SELF)), null)
            assertEquals(expected, result.profitAfterShipping)
            assertEquals(25000L, result.shippingCollected)
        }
    }

    @Test
    fun `unknown shipping expense and zero shipping expense are different`() {
        val unknown = SellerFinanceCalculator.calculate(listOf(row(expense = null)), null)
        assertEquals(60000.0, unknown.profitAfterShipping)
        assertEquals(0L, unknown.shippingMargin)
        assertNull(unknown.orders.single().actualShippingCost)
        assertEquals(1, unknown.missingShippingCostOrders)
        assertEquals(60000.0, unknown.grossProfit)
        val zero = SellerFinanceCalculator.calculate(listOf(row(expense = 0)), null)
        assertEquals(85000.0, zero.profitAfterShipping)
        assertEquals(0, zero.missingShippingCostOrders)
        val updated = row(expense = null)
        assertEquals(0L, SellerFinanceCalculator.calculate(listOf(updated), null).shippingMargin)
        updated.order.actualShippingCost = 35000L
        val confirmed = SellerFinanceCalculator.calculate(listOf(updated), null)
        assertEquals(-10000L, confirmed.shippingMargin)
        assertEquals(50000.0, confirmed.profitAfterShipping)
        assertEquals(0, confirmed.missingShippingCostOrders)
    }

    @Test
    fun `missing cost of goods is zero while the original snapshot stays null`() {
        val result = SellerFinanceCalculator.calculate(listOf(row(cost = null)), null)
        assertEquals(200000.0, result.grossProfit)
        assertEquals(0.0, result.costOfGoods)
        assertEquals(195000.0, result.profitAfterShipping)
        assertEquals(200000.0, result.merchandiseRevenue)
        assertEquals(200000.0, result.orders.single().grossProfit)
        assertEquals(195000.0, result.orders.single().profitAfterShipping)
        assertEquals(1, result.missingCostItems)
        assertEquals(-5000L, result.shippingMargin)
    }

    @Test
    fun `cancelled orders have zero revenue and keep delivery loss`() {
        val result = SellerFinanceCalculator.calculate(listOf(row(status = "CANCELLED", cost = null)), null)
        assertEquals(0.0, result.merchandiseRevenue)
        assertEquals(0L, result.shippingCollected)
        assertEquals(0.0, result.grossProfit)
        assertEquals(30000L, result.cancelledShippingCost)
        assertEquals(-30000.0, result.profitAfterShipping)
    }

    @Test
    fun `multiple lines charge shipping once and retain the order cost snapshots`() {
        val first = row()
        val second = OrderItem(order = first.order, product = product, quantity = 1.0, price = 100000.0, costPrice = 60000.0)
        val result = SellerFinanceCalculator.calculate(listOf(first, second), null)
        assertEquals(300000.0, result.merchandiseRevenue)
        assertEquals(25000L, result.shippingCollected)
        assertEquals(30000L, result.recordedShippingCost)
        assertEquals(95000.0, result.profitAfterShipping)
    }

    @Test
    fun `month filter excludes other months and unfinished orders`() {
        val result = SellerFinanceCalculator.calculate(listOf(row(), row(id = 2, month = 9), row(id = 3, status = "SHIPPING")), 10)
        assertEquals(1, result.orders.size)
        assertEquals(55000.0, result.profitAfterShipping)
    }

    @Test
    fun `summary preserves known shipping loss and marks missing expense as provisional`() {
        val result = SellerFinanceCalculator.calculate(listOf(row(), row(id = 2, status = "CANCELLED", expense = null)), null)
        assertEquals(55000.0, result.profitAfterShipping)
        assertEquals(-5000L, result.shippingMargin)
        assertEquals(0L, result.orders.first { it.orderId == 2L }.shippingMargin)
        assertEquals(55000.0, result.orders.first { it.orderId == 1L }.profitAfterShipping)
        assertEquals(1, result.missingShippingCostOrders)
    }

    @Test
    fun `old orders without costs use provisional zero shipping margin`() {
        val result = SellerFinanceCalculator.calculate(listOf(row(), row(id = 2, cost = null, expense = null)), null)
        assertEquals(2, result.orders.size)
        assertEquals(400000.0, result.merchandiseRevenue)
        assertEquals(140000.0, result.costOfGoods)
        assertEquals(260000.0, result.grossProfit)
        assertEquals(255000.0, result.profitAfterShipping)
        assertEquals(-5000L, result.shippingMargin)
        assertEquals(1, result.missingShippingCostOrders)
        assertEquals(200000.0, result.orders.first { it.orderId == 2L }.grossProfit)
    }

    @Test
    fun `one missing item uses zero cost and keeps all other item costs and shipping`() {
        val first = row(id = 2)
        val missing = OrderItem(order = first.order, product = product, quantity = 1.0, price = 100000.0, costPrice = null)
        val result = SellerFinanceCalculator.calculate(listOf(row(), first, missing), null)
        assertEquals(500000.0, result.merchandiseRevenue)
        assertEquals(210000.0, result.profitAfterShipping)
        assertEquals(220000.0, result.grossProfit)
        assertEquals(280000.0, result.costOfGoods)
        assertEquals(2, result.orders.size)
        assertEquals(160000.0, result.orders.first { it.orderId == 2L }.grossProfit)
    }

    @Test
    fun `changing product cost never rewrites old snapshots or fills unknown historical costs`() {
        product.costPrice = 140000.0
        val old = row(cost = product.costPrice)
        val unknown = row(id = 2, cost = null)
        product.costPrice = 100000.0
        val fresh = row(id = 3, cost = product.costPrice)
        val result = SellerFinanceCalculator.calculate(listOf(old, unknown, fresh), null)
        assertEquals(140000.0, old.costPrice)
        assertNull(unknown.costPrice)
        assertEquals(100000.0, fresh.costPrice)
        assertEquals(55000.0, result.orders.first { it.orderId == 1L }.profitAfterShipping)
        assertEquals(95000.0, result.orders.first { it.orderId == 3L }.profitAfterShipping)
        assertEquals(195000.0, result.orders.first { it.orderId == 2L }.profitAfterShipping)
        assertEquals(345000.0, result.profitAfterShipping)
        assertEquals(600000.0, result.merchandiseRevenue)
    }

    @Test
    fun `explicit zero is a valid recorded cost for profit`() {
        val result = SellerFinanceCalculator.calculate(listOf(row(cost = 0.0)), null)
        assertEquals(200000.0, result.grossProfit)
        assertEquals(195000.0, result.profitAfterShipping)
        assertEquals(0, result.missingCostItems)
    }
}
