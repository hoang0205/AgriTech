package com.uet.agritech.statistics

import com.uet.agritech.order.Order
import com.uet.agritech.order.OrderItem
import com.uet.agritech.order.StatisticsOverviewService
import com.uet.agritech.product.Product
import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SellerTaxCalculatorTest {
    private fun item(
        revenue: Double, cost: Double? = 1.0, status: String = "COMPLETED",
        year: Int = 2026, quantity: Double = 1.0, month: Int = 1
    ): OrderItem {
        val order = mockk<Order>(relaxed = true)
        every { order.id } returns month.toLong()
        every { order.status } returns status
        every { order.orderDate } returns LocalDateTime.of(year, month, 10, 12, 0)
        every { order.deliveryMethod } returns null
        every { order.actualShippingCost } returns 0L
        val buyer = mockk<User>(relaxed = true)
        every { buyer.fullName } returns "Khách mua"
        every { buyer.avatarUrl } returns "https://example.test/buyer.jpg"
        every { order.user } returns buyer
        every { order.phoneNumber } returns "0900000001"
        val product = mockk<Product>(relaxed = true)
        every { product.id } returns "product"
        every { product.imageUrls } returns mutableListOf("https://example.test/product.jpg")
        // Today's product cost must never replace the cost snapshot on the order.
        every { product.costPrice } returns 999.0
        return OrderItem(order = order, product = product, quantity = quantity, price = revenue, costPrice = cost)
    }

    @Test
    fun `exact threshold and below do not incur estimated tax`() {
        for (revenue in listOf(0.0, 999_999_999.0, 1_000_000_000.0)) {
            val tax = SellerTaxCalculator.calculate(listOf(item(revenue)), 2026, 20.0)
            assertEquals("BELOW_THRESHOLD", tax.status)
            assertEquals(0.0, tax.totalTax)
            assertEquals(20.0, tax.annualProfitAfterEstimatedTax)
        }
    }

    @Test
    fun `tax uses full VAT base excess income base and temporary reduction`() {
        val tax = SellerTaxCalculator.calculate(listOf(item(1_200_000_000.0)), 2026, 200_000_000.0)
        assertEquals("ESTIMATED", tax.status)
        assertEquals(12_000_000.0, tax.vat)
        assertEquals(200_000_000.0, tax.incomeTaxBase)
        assertEquals(1_000_000.0, tax.incomeTaxBeforeReduction)
        assertEquals(300_000.0, tax.incomeTaxReduction)
        assertEquals(700_000.0, tax.incomeTax)
        assertEquals(12_700_000.0, tax.totalTax)
        assertEquals(187_300_000.0, tax.annualProfitAfterEstimatedTax)
    }

    @Test
    fun `annual base aggregates months and excludes cancelled pending other year and missing costs`() {
        val tax = SellerTaxCalculator.calculate(listOf(
            item(600_000_000.0, month = 1), item(600_000_000.0, month = 12),
            item(800_000_000.0, cost = null), item(100.0, cost = -1.0),
            item(200.0, cost = Double.NaN), item(300.0, cost = Double.POSITIVE_INFINITY),
            item(2_000_000_000.0, status = "CANCELLED"),
            item(2_000_000_000.0, status = "PENDING"), item(2_000_000_000.0, year = 2025)
        ), 2026, null)
        assertEquals(1_200_000_000.0, tax.annualRevenue)
        assertEquals(800_000_600.0, tax.excludedRevenue)
        assertEquals(2, tax.qualifyingItems)
        assertEquals(12_700_000.0, tax.totalTax)
        assertNull(tax.annualProfitAfterEstimatedTax)
    }

    @Test
    fun `zero and missing costs stay outside tax revenue even if product has a cost today`() {
        val tax = SellerTaxCalculator.calculate(listOf(
            item(1_200_000_000.0, cost = 0.0), item(100.0, cost = null), item(50.0)
        ), 2026, null)
        assertEquals(50.0, tax.annualRevenue)
        assertEquals(1_200_000_100.0, tax.excludedRevenue)
        assertEquals(1, tax.qualifyingItems)
        assertEquals("BELOW_THRESHOLD", tax.status)
        assertEquals(0.0, tax.totalTax)
    }

    @Test
    fun `three billion is supported but higher requires income method`() {
        val atLimit = SellerTaxCalculator.calculate(listOf(item(3_000_000_000.0)), 2026, null)
        assertEquals("ESTIMATED", atLimit.status)
        val aboveLimit = SellerTaxCalculator.calculate(listOf(item(3_000_000_001.0)), 2026, 500.0)
        assertEquals("INCOME_METHOD_REQUIRED", aboveLimit.status)
        assertEquals(30_000_000.0, aboveLimit.vat)
        assertNull(aboveLimit.incomeTax)
        assertNull(aboveLimit.totalTax)
        assertNull(aboveLimit.annualProfitAfterEstimatedTax)
    }

    @Test
    fun `temporary reduction expires after 2027 and older policy is not extrapolated`() {
        for (year in listOf(2026, 2027, 2028)) {
            val tax = SellerTaxCalculator.calculate(listOf(item(1_200_000_000.0, year = year)), year, null)
            assertEquals(if (year <= 2027) 700_000.0 else 1_000_000.0, tax.incomeTax)
        }
        val historical = SellerTaxCalculator.calculate(listOf(item(1_200_000_000.0, year = 2025)), 2025, 500.0)
        assertEquals("OUTSIDE_POLICY_YEAR", historical.status)
        assertNull(historical.totalTax)
        assertNull(historical.vat)
    }

    @Test
    fun `fractional quantities use decimal threshold and round tax to dong`() {
        val tax = SellerTaxCalculator.calculate(listOf(item(100_000_000.0, quantity = 10.00000001)), 2026, null)
        assertEquals(1_000_000_001.0, tax.annualRevenue)
        assertEquals("ESTIMATED", tax.status)
        assertEquals(10_000_000.0, tax.vat)
        assertEquals(0.0, tax.incomeTax)
    }

    @Test
    fun `overview keeps all sales in total revenue while month filter never resets tax threshold`() {
        val user = mockk<User>()
        val users = mockk<UserRepository>()
        val repository = mockk<StatisticsOverviewRepository>()
        val items = listOf(
            item(600_000_000.0, month = 1), item(600_000_000.0, month = 12),
            item(800_000_000.0, cost = 0.0), item(100_000_000.0, cost = null)
        )
        every { users.findByPhoneNumber("seller") } returns Optional.of(user)
        every { repository.findBuyerItems(user, any(), any()) } returns emptyList()
        every { repository.findSellerItems(user, any(), any()) } returns items
        every { repository.findSellerFinanceItems(user, any(), any()) } returns items
        val service = StatisticsOverviewService(users, repository)
        val annual = service.getOverview("seller", 2026, null)
        val january = service.getOverview("seller", 2026, 1)
        assertEquals(2_100_000_000.0, annual.revenue?.totalAmount)
        assertEquals(2_100_000_000.0, annual.sellerFinance?.merchandiseRevenue)
        assertEquals(1_500_000_000.0, january.revenue?.totalAmount)
        assertEquals(1_200_000_000.0, annual.sellerTax?.annualRevenue)
        assertEquals(900_000_000.0, annual.sellerTax?.excludedRevenue)
        assertEquals(annual.sellerTax, january.sellerTax)
        assertEquals(12_700_000.0, january.sellerTax?.totalTax)
        // Missing historical costs contribute zero expense to profit, while tax
        // revenue still includes only the lines whose recorded cost is positive.
        assertEquals(1_499_999_999.0, january.profit?.totalAmount)
        assertEquals(1_499_999_999.0, january.profit?.monthlyAmounts?.first()?.amount)
        assertEquals(1, january.profit?.orders?.size)
        assertEquals(1_400_000_000.0, january.profit?.knownCostRevenue)
        assertEquals(2_099_999_998.0, annual.profit?.totalAmount)
        assertEquals(2_099_999_998.0, annual.sellerFinance?.grossProfit)
        assertEquals(2, annual.sellerFinance?.orders?.size)
        val order = requireNotNull(annual.revenue).orders.first()
        assertEquals("Khách mua", order.buyerName)
        assertEquals("https://example.test/buyer.jpg", order.buyerAvatar)
        assertEquals("0900000001", order.buyerPhone)
        assertEquals("https://example.test/product.jpg", order.items.first().imageUrl)
        val financeOrder = requireNotNull(annual.sellerFinance).orders.first()
        assertEquals(order.buyerName, financeOrder.buyerName)
        assertEquals(order.items.first().imageUrl, financeOrder.items.first().imageUrl)
    }
}
