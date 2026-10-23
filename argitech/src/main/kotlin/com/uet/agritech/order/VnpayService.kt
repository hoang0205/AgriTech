package com.uet.agritech.order

import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class VnpayService(private val orders: OrderRepository) {
    @org.springframework.transaction.annotation.Transactional
    fun createForOrder(id: Long, phone: String, ip: String): String {
        val order = orders.findForStatusUpdate(id).orElseThrow { IllegalArgumentException("Order not found") }
        require(order.user.phoneNumber == phone) { "Forbidden" }
        require(order.status == "UNPAID" && order.paymentMethod == "VNPAY" && order.ghnRequestJson == null) { "Order is not payable" }
        val ref = order.vnpayTxnRef ?: "${id}_${UUID.randomUUID().toString().replace("-", "")}"
        order.vnpayTxnRef = ref
        return createPaymentUrl(id, order.totalAmount.toLong(), ip, ref)
    }

    fun validSignature(params: Map<String, String>): Boolean {
        val signature = params["vnp_SecureHash"] ?: return false
        val data = params.filter { (key, value) -> key.startsWith("vnp_") && key !in setOf("vnp_SecureHash", "vnp_SecureHashType") && value.isNotEmpty() }
            .toSortedMap().entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, StandardCharsets.US_ASCII)}" }
        val expected = VnpayConfig.hmacSHA512(VnpayConfig.vnp_HashSecret, data)
        return java.security.MessageDigest.isEqual(expected.toByteArray(), signature.lowercase().toByteArray())
    }

    @org.springframework.transaction.annotation.Transactional
    fun processResult(params: Map<String, String>, expectedId: Long? = null, phone: String? = null): Map<String, String> {
        fun result(code: String, message: String) = mapOf("RspCode" to code, "Message" to message)
        if (!validSignature(params) || params["vnp_TmnCode"] != VnpayConfig.vnp_TmnCode) return result("97", "Invalid signature")
        val ref = params["vnp_TxnRef"] ?: return result("01", "Missing reference")
        val id = ref.substringBefore('_').toLongOrNull() ?: return result("01", "Invalid reference")
        if (expectedId != null && id != expectedId) return result("01", "Wrong order")
        val order = orders.findForStatusUpdate(id).orElse(null) ?: return result("01", "Order not found")
        if (phone != null && order.user.phoneNumber != phone) return result("01", "Forbidden")
        if (order.vnpayTxnRef != ref || order.paymentMethod != "VNPAY") return result("01", "Wrong transaction")
        if (params["vnp_Amount"]?.toLongOrNull() != order.totalAmount.toLong() * 100) return result("04", "Wrong amount")
        if (order.vnpayTransactionNo != null) return result("00", "Already paid")
        if (order.status != "UNPAID") return result("02", "Invalid order status")
        if (params["vnp_ResponseCode"] == "00" && params["vnp_TransactionStatus"] == "00") {
            val transaction = params["vnp_TransactionNo"]?.takeIf { it.isNotBlank() && it != "0" } ?: return result("99", "Missing transaction number")
            order.vnpayTransactionNo = transaction
            order.status = "PENDING"
        }
        return result("00", "Confirmed")
    }


    fun createPaymentUrl(
        orderId: Long?,
        amount: Long,
        ipAddress: String = "127.0.0.1",
        txnRef: String = "${orderId}_${System.currentTimeMillis()}"
    ): String {
        requireNotNull(orderId)

        val vnpVersion = "2.1.0"
        val vnpCommand = "pay"
        val orderType = "other"
        val vnpAmount = (amount * 100).toString()
        val vnpTxnRef = txnRef

        val orderInfo = "ThanhToanDonHang$orderId"
        val returnUrl = "https://agritech.com.vn/payment-result"
        val cleanIp = if (ipAddress.contains(":") || ipAddress.isBlank()) "127.0.0.1" else ipAddress

        val vnpParams: MutableMap<String, String> = HashMap()
        vnpParams["vnp_Version"] = vnpVersion
        vnpParams["vnp_Command"] = vnpCommand
        vnpParams["vnp_TmnCode"] = VnpayConfig.vnp_TmnCode
        vnpParams["vnp_Amount"] = vnpAmount
        vnpParams["vnp_CurrCode"] = "VND"
        vnpParams["vnp_TxnRef"] = vnpTxnRef
        vnpParams["vnp_OrderInfo"] = orderInfo
        vnpParams["vnp_OrderType"] = orderType
        vnpParams["vnp_Locale"] = "vn"
        vnpParams["vnp_ReturnUrl"] = returnUrl
        vnpParams["vnp_IpAddr"] = cleanIp
        vnpParams["vnp_CreateDate"] = VnpayConfig.getCurrentTimeString()

        val fieldNames = ArrayList(vnpParams.keys)
        Collections.sort(fieldNames)

        val hashData = StringBuilder()
        val query = StringBuilder()

        for (fieldName in fieldNames) {
            val fieldValue = vnpParams[fieldName]
            if (!fieldValue.isNullOrBlank()) {
                val encodedField = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString())
                val encodedValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString())

                hashData.append(fieldName).append('=').append(encodedValue).append('&')
                query.append(encodedField).append('=').append(encodedValue).append('&')
            }
        }

        if (hashData.isNotEmpty()) hashData.deleteCharAt(hashData.length - 1)
        if (query.isNotEmpty()) query.deleteCharAt(query.length - 1)

        val vnpSecureHash = VnpayConfig.hmacSHA512(VnpayConfig.vnp_HashSecret, hashData.toString())
        query.append("&vnp_SecureHash=").append(vnpSecureHash)

        val paymentUrl = "${VnpayConfig.vnp_PayUrl}?$query"
        return paymentUrl
    }
}