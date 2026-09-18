package com.uet.agritech.order

import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

@Service
class VnpayService {

    fun createPaymentUrl(
        orderId: Long?,
        amount: Long,
        ipAddress: String = "127.0.0.1"
    ): String {
        requireNotNull(orderId)

        val vnpVersion = "2.1.0"
        val vnpCommand = "pay"
        val orderType = "other"
        val vnpAmount = (amount * 100).toString()
        val vnpTxnRef = "${orderId}_${System.currentTimeMillis()}"

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
        println("LINK VNPAY: $paymentUrl")
        return paymentUrl
    }
}