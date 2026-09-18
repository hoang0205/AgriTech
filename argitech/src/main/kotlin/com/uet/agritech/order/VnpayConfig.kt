package com.uet.agritech.order

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object VnpayConfig {
    const val vnp_PayUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
    const val vnp_TmnCode = "PVTGE31B"
    const val vnp_HashSecret = "ELBYLSAZTGRXRXKZTQNHQCEEXZMWTROB"

    fun hmacSHA512(key: String, data: String): String {
        val hmac512 = Mac.getInstance("HmacSHA512")
        val secretKey = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA512")
        hmac512.init(secretKey)
        val hash = hmac512.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

        fun getCurrentTimeString(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        val formatter = SimpleDateFormat("yyyyMMddHHmmss")
        formatter.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        return formatter.format(calendar.time)
    }
}