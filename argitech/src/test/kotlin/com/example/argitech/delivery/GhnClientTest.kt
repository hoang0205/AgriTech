package com.uet.agritech.delivery.ghn

import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.*

class GhnClientTest {
    private val client = GhnClient(GhnSettings(true, "test", "staging", "seller=123"))
    private val lookup = "/v2/shipping-order/detail-by-client-code?client_order_code=AG-test"

    @Test
    fun `connection errors identify timeout DNS and TLS without exposing raw exception text`() {
        val failures = listOf(
            java.net.http.HttpConnectTimeoutException("private-token") to "quá thời gian chờ",
            java.io.IOException("private-token", java.net.UnknownHostException("private-token")) to "DNS",
            javax.net.ssl.SSLHandshakeException("private-token") to "bảo mật",
            java.net.ConnectException("private-token") to "chưa kết nối"
        )
        for ((failure, hint) in failures) {
            val error = client.transportFailure("/v2/shop/all", failure)
            assertTrue(error.reason!!.contains(hint))
            assertFalse(error.reason!!.contains("private-token"))
        }
    }

    @Test
    fun `upstream authentication rate limit and server failure are distinguished`() {
        for ((status, hint) in listOf(401 to "xác thực", 403 to "quyền", 429 to "giới hạn", 503 to "Máy chủ GHN")) {
            val error = assertFailsWith<ResponseStatusException> {
                client.decodeResponse("/v2/shop/all", status, """{"code":$status,"message":"private-token"}""")
            }
            assertTrue(error.reason!!.contains(hint))
            assertFalse(error.reason!!.contains("private-token"))
        }
    }

    @Test
    fun `only explicit not found on lookup endpoint permits absence classification`() {
        val body = """{"code":400,"message":"Đơn hàng không tồn tại","data":null}"""
        assertFailsWith<GhnOrderNotFoundException> { client.decodeResponse(lookup, 400, body) }
        assertFailsWith<ResponseStatusException> { client.decodeResponse("/v2/shipping-order/create", 400, body) }
        assertFailsWith<ResponseStatusException> { client.decodeResponse(lookup, 403, body) }
        assertFailsWith<ResponseStatusException> { client.decodeResponse(lookup, 500, body) }
    }

    @Test
    fun `validation rejection is distinct from server and authentication errors`() {
        val body = """{"code":400,"code_message":"WARD_IS_INVALID","message":"raw private data"}"""
        val error = assertFailsWith<GhnRejectedException> { client.decodeResponse("/v2/shipping-order/create", 400, body) }
        assertFalse(error.reason!!.contains("raw private data"))
        val serverError = assertFailsWith<ResponseStatusException> {
            client.decodeResponse("/v2/shipping-order/create", 500, body)
        }
        assertFalse(serverError is GhnRejectedException)
        val ambiguous = assertFailsWith<ResponseStatusException> {
            client.decodeResponse("/v2/shipping-order/create", 400,
                """{"code":400,"code_message":"SERVER_ERROR_COMMON","message":"service failed"}""")
        }
        assertFalse(ambiguous is GhnRejectedException)
    }

    @Test
    fun `empty invalid and generic 404 responses remain uncertain`() {
        for (body in listOf("", "null", "<html>Not Found</html>", """{"code":404,"message":"Not found"}""")) {
            assertFailsWith<ResponseStatusException> { client.decodeResponse(lookup, 404, body) }
        }
    }

    @Test
    fun `common validation errors retain the exact invalid field`() {
        val body = """{"code":400,"code_message":"USER_ERR_COMMON","message":"Key: 'myRequest.Items' Error:Field validation for 'Items' failed on the 'required' tag"}"""
        val error = assertFailsWith<GhnRejectedException> {
            client.decodeResponse("/v2/shipping-order/create", 400, body)
        }
        assertTrue(error.reason!!.contains("Items"))
        assertTrue(error.reason!!.contains("required"))
        assertTrue(error.reason!!.contains("USER_ERR_COMMON"))
    }

    @Test
    fun `common error diagnostics redact token and submitted customer details`() {
        val token = "8bc34567-0123-4567-89ab-123456789abc"
        val secureClient = GhnClient(GhnSettings(true, token, "staging", "seller=123"))
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val request = mapper.readTree("""{"to_name":"Nguyen Van A","to_phone":"0912345678","to_address":"123 Test Street","content":"private goods"}""")
        val response = mapper.createObjectNode().put("code", 400).put("code_message", "USER_ERR_COMMON")
            .put("message", "to_address invalid: 123 Test Street; Nguyen Van A 0912345678 token $token; private goods")
        val error = assertFailsWith<GhnRejectedException> {
            secureClient.decodeResponse("/v2/shipping-order/create", 400, response.toString(), request)
        }
        for (secret in listOf(token, "Nguyen Van A", "0912345678", "123 Test Street", "private goods")) {
            assertFalse(error.reason!!.contains(secret))
        }
        assertTrue(error.reason!!.contains("to_address"))
    }
}
