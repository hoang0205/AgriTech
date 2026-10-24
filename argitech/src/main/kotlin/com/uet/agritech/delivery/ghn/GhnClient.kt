package com.uet.agritech.delivery.ghn

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class GhnSettings(
    @Value("\${ghn.enabled:false}") val enabled: Boolean,
    @Value("\${GHN_TOKEN:}") val token: String,
    @Value("\${GHN_ENVIRONMENT:staging}") val environment: String,
    @Value("\${GHN_SELLER_SHOPS:}") sellerShops: String,
    private val sellerShopLinks: GhnSellerShopRepository? = null
) {
    // Explicit assignment by the operator; a seller cannot select another shop.
    private val shops = sellerShops.split(',').filter { it.isNotBlank() }.associate {
        val parts = it.trim().split('=')
        require(parts.size == 2 && parts[0].isNotBlank()) { "GHN_SELLER_SHOPS: sellerPhone=shopId,..." }
        parts[0] to parts[1].toInt().also { id -> require(id > 0) }
    }
    val baseUrl: String get() = when (environment) {
        "staging" -> "https://dev-online-gateway.ghn.vn/shiip/public-api"
        "production" -> "https://online-gateway.ghn.vn/shiip/public-api"
        else -> throw IllegalStateException("GHN_ENVIRONMENT must be staging or production")
    }
    fun shopFor(sellerPhone: String): Int {
        requireEnabled()
        return configuredShopFor(sellerPhone) ?: throw ResponseStatusException(
            HttpStatus.CONFLICT, "Hãy kết nối cửa hàng GHN trước khi gửi hàng."
        )
    }
    fun requireEnabled() {
        if (!enabled || token.isBlank()) throw ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "GHN chưa được bật hoặc chưa cấu hình Token ở máy chủ."
        )
    }
    fun configuredShopFor(phone: String): Int? =
        sellerShopLinks?.findById("$environment:$phone")?.orElse(null)?.shopId ?: shops[phone]
}

class GhnRejectedException(reason: String) : ResponseStatusException(HttpStatus.BAD_GATEWAY, reason)
class GhnOrderNotFoundException : RuntimeException()

@Component
class GhnClient(private val settings: GhnSettings) {
    private val mapper = ObjectMapper()
    private val logger = org.slf4j.LoggerFactory.getLogger(GhnClient::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun get(path: String, shopId: Int): JsonNode = call(path, shopId, null)
    fun post(path: String, shopId: Int, body: JsonNode): JsonNode = call(path, shopId, body)

    private fun call(path: String, shopId: Int, body: JsonNode?): JsonNode {
        val builder = HttpRequest.newBuilder(URI.create(settings.baseUrl + path))
            .timeout(Duration.ofSeconds(20))
            .header("Token", settings.token).header("ShopId", shopId.toString())
            .header("Content-Type", "application/json")
        if (body == null) builder.GET() else builder.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        val response = try {
            http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw unavailable()
        } catch (e: java.io.IOException) {
            throw transportFailure(path, e)
        }
        return decodeResponse(path, response.statusCode(), response.body(), body)
    }

    internal fun transportFailure(path: String, error: java.io.IOException): ResponseStatusException {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
        val category = when {
            causes.any { it is java.net.http.HttpTimeoutException || it is java.net.SocketTimeoutException } -> "TIMEOUT"
            causes.any { it is java.net.UnknownHostException || it is java.nio.channels.UnresolvedAddressException } -> "DNS"
            causes.any { it is javax.net.ssl.SSLException } -> "TLS"
            causes.any { it is java.net.ConnectException } -> "CONNECT"
            else -> "NETWORK"
        }
        // Exception text can contain URLs or credentials. Log only known categories/types.
        logger.warn("GHN connection failed: endpoint={} category={} types={}",
            path.substringBefore('?'), category, causes.joinToString(" -> ") { it.javaClass.simpleName })
        val reason = when (category) {
            "TIMEOUT" -> "Kết nối GHN quá thời gian chờ. Thử lại sau hoặc chọn tự tổ chức giao."
            "DNS" -> "Máy chủ AgriTech chưa phân giải được địa chỉ GHN. Kiểm tra mạng/DNS trên máy chạy backend."
            "TLS" -> "Máy chủ AgriTech không thiết lập được kết nối bảo mật với GHN. Kiểm tra mạng và chứng chỉ trên backend."
            else -> "Máy chủ AgriTech chưa kết nối được GHN. Thử lại sau hoặc chọn tự tổ chức giao."
        }
        return ResponseStatusException(HttpStatus.BAD_GATEWAY, reason)
    }

    internal fun decodeResponse(path: String, httpStatus: Int, body: String, request: JsonNode? = null): JsonNode {
        val root = (try { mapper.readTree(body) } catch (e: Exception) { throw unavailable() })
            ?: throw unavailable()
        if (httpStatus !in 200..299 || root.path("code").asInt() != 200) {
            val status = root.path("code").asInt()
            val errorCode = root.path("code_message").asText()
            val message = root.path("message").asText().trim()
            val safeCode = errorCode.takeIf { it.matches(Regex("[A-Z0-9_]{1,64}")) } ?: "GHN_ERROR"
            val detail = safeDetail(message, request)
            logger.warn("GHN rejected request: endpoint={} http={} upstreamCode={} detail={}",
                path.substringBefore('?'), httpStatus, safeCode, detail)
            if (httpStatus in setOf(401, 403) || status in setOf(401, 403)) throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "GHN không chấp nhận xác thực hoặc quyền truy cập. Kiểm tra Token, Shop ID và môi trường của backend.")
            if (httpStatus == 429 || status == 429) throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "GHN đang giới hạn số yêu cầu. Hãy thử lại sau.")
            if (httpStatus >= 500 || status >= 500) throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Máy chủ GHN đang gặp lỗi. Thử lại sau hoặc chọn tự tổ chức giao.")
            // Only an explicit not-found response from the lookup endpoint can release
            // a persisted intent. Auth failures, timeouts and generic 4xx/5xx cannot.
            if (path.startsWith("/v2/shipping-order/detail-by-client-code?") &&
                httpStatus in setOf(400, 404) && status in setOf(400, 404) &&
                (errorCode == "ORDER_NOT_FOUND" || message in setOf("Đơn hàng không tồn tại", "Không tìm thấy đơn hàng") ||
                    message.endsWith(" - Đơn hàng không tồn tại"))) {
                throw GhnOrderNotFoundException()
            }
            if (httpStatus in setOf(400, 422) && status in setOf(400, 422) &&
                errorCode != "SERVER_ERROR_COMMON") {
                throw GhnRejectedException(when (errorCode) {
                    "FROM_ADDRESS_CONVERT_FAIL" -> "GHN từ chối [FROM_ADDRESS_CONVERT_FAIL]: Hãy chuẩn hóa địa chỉ lấy hàng trong phần kết nối shop GHN rồi báo giá lại."
                    "PHONE_INVALID" -> "GHN từ chối: số điện thoại liên hệ không hợp lệ."
                    "WARD_IS_INVALID" -> "GHN từ chối: phường/xã và quận/huyện không khớp."
                    "SHOP_NO_ACTIVE" -> "GHN từ chối: shop chưa hoạt động."
                    "CLIENT_NOT_BELONG_OF_SHOP", "CLIENT_NOT_OWNER_OF_SHOP" -> "GHN từ chối: Token không có quyền sử dụng shop này."
                    else -> "GHN từ chối [$safeCode]: ${detail.ifBlank { "Không có lý do chi tiết từ GHN." }}"
                })
            }
            // Never forward raw upstream responses: they may contain customer data or credentials.
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "GHN từ chối yêu cầu. Kiểm tra cấu hình shop, địa chỉ, kiện hàng và trạng thái trên GHN.")
        }
        return root.get("data")?.takeUnless { it.isNull } ?: throw unavailable()
    }

    private fun safeDetail(message: String, request: JsonNode?): String {
        val secrets = mutableSetOf<String>()
        if (settings.token.isNotBlank()) secrets += settings.token
        fun collect(node: JsonNode) {
            if (node.isObject) node.fields().forEachRemaining { (key, value) ->
                if (value.isTextual && listOf("name", "phone", "address", "content", "code", "note")
                        .any { key.contains(it, ignoreCase = true) }) {
                    value.asText().takeIf { it.isNotBlank() }?.let { secrets += it }
                }
                if (value.isContainerNode) collect(value)
            } else if (node.isArray) node.forEach(::collect)
        }
        if (request != null) collect(request)
        var result = message
        for (value in secrets.sortedByDescending { it.length }) result = result.replace(value, "[đã che]", ignoreCase = true)
        return result
            .replace(Regex("https?://\\S+"), "[đã che URL]")
            .replace(Regex("[A-Za-z0-9_.+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[đã che email]")
            .replace(Regex("[A-Za-z0-9_-]{24,}(?:\\.[A-Za-z0-9_-]+)*"), "[đã che mã]")
            .replace(Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"), "[đã che mã]")
            .replace(Regex("\\+?\\d[\\d .()-]{5,}\\d"), "[đã che số]")
            .replace(Regex("\\p{Cntrl}"), " ").take(400)
    }

    private fun unavailable() = ResponseStatusException(HttpStatus.BAD_GATEWAY,
        "Chưa nhận được phản hồi hợp lệ từ GHN. Thử lại sau hoặc chọn tự tổ chức giao.")
}
