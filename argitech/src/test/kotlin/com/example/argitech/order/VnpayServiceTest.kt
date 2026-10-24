package com.uet.agritech.order

import io.mockk.*
import org.junit.jupiter.api.Test
import java.util.Optional
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals

class VnpayServiceTest {
    private val orders = mockk<OrderRepository>()
    private val service = VnpayService(orders)
    private val order = mockk<Order>(relaxed = true)

    private fun callback(amount: String = "10000000", response: String = "00"): Map<String, String> {
        val values = sortedMapOf("vnp_TmnCode" to VnpayConfig.vnp_TmnCode, "vnp_TxnRef" to "1_test",
            "vnp_Amount" to amount, "vnp_ResponseCode" to response, "vnp_TransactionStatus" to response,
            "vnp_TransactionNo" to "12345")
        val data = values.entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.US_ASCII)}" }
        return values + ("vnp_SecureHash" to VnpayConfig.hmacSHA512(VnpayConfig.vnp_HashSecret, data))
    }
    private fun prepare() {
        every { orders.findForStatusUpdate(1) } returns Optional.of(order)
        every { order.vnpayTxnRef } returns "1_test"
        every { order.paymentMethod } returns "VNPAY"
        every { order.totalAmount } returns 100000.0
        every { order.status } returns "UNPAID"
        every { order.vnpayTransactionNo } returns null
    }
    @Test fun `forged signature never accesses order`() {
        assertEquals("97", service.processResult(callback() + ("vnp_Amount" to "1"))["RspCode"])
        verify { orders wasNot Called }
    }
    @Test fun `wrong amount does not mark paid`() {
        prepare()
        assertEquals("04", service.processResult(callback("1"))["RspCode"])
        verify(exactly = 0) { order.status = any() }
    }
    @Test fun `verified payment moves order to pending`() {
        prepare()
        assertEquals("00", service.processResult(callback())["RspCode"])
        verify(exactly = 1) { order.status = "PENDING"; order.vnpayTransactionNo = "12345" }
    }
    @Test fun `duplicate payment does not update twice`() {
        prepare()
        every { order.vnpayTransactionNo } returns "12345"
        assertEquals("00", service.processResult(callback())["RspCode"])
        verify(exactly = 0) { order.status = any() }
    }
    @Test fun `cancel leaves order unpaid`() {
        prepare()
        assertEquals("00", service.processResult(callback(response = "24"))["RspCode"])
        verify(exactly = 0) { order.status = any() }
    }
    @Test fun `another buyer cannot verify payment`() {
        prepare()
        every { order.user.phoneNumber } returns "owner"
        assertEquals("01", service.processResult(callback(), expectedId = 1, phone = "other")["RspCode"])
        verify(exactly = 0) { order.status = any() }
    }
    @Test fun `wrong order id is rejected`() {
        assertEquals("01", service.processResult(callback(), expectedId = 2)["RspCode"])
        verify { orders wasNot Called }
    }
}
