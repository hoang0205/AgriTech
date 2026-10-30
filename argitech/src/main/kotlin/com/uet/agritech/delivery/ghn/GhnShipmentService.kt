package com.uet.agritech.delivery.ghn

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.uet.agritech.order.Order
import com.uet.agritech.order.DeliveryMethod
import com.uet.agritech.order.OrderItem
import com.uet.agritech.order.OrderItemRepository
import com.uet.agritech.order.OrderRepository
import com.uet.agritech.order.isGhnSimulation
import com.uet.agritech.order.isAwaitingOnlinePayment
import com.uet.agritech.order.requirePaymentForFulfillment
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.roundToLong

data class GhnParcelRequest(
    val provinceId: Int, val districtId: Int = 0, val wardCode: String,
    val weight: Int, val length: Int, val width: Int, val height: Int,
    val expectedFee: Long? = null
) {
    fun validate() {
        require(provinceId > 0 && districtId == 0 && wardCode.matches(Regex("[0-9]{1,20}"))) {
            "Vui lòng cập nhật app và chọn tỉnh/thành, phường/xã từ danh mục GHN mới (không có quận/huyện)."
        }
        require(weight in 1..19999) { "Bản tích hợp này hỗ trợ một kiện dưới 20 kg (nhập gram)." }
        require(listOf(length, width, height).all { it in 1..200 }) { "Kích thước kiện phải từ 1 đến 200 cm." }
    }
}
data class GhnArea(val id: String, val name: String)
data class GhnQuote(val fee: Long, val customerShippingFee: Long, val codAmount: Long, val environment: String)
data class GhnShipmentView(
    val orderCode: String?, val status: String?, val shippingFee: Long?,
    val expectedDeliveryTime: String?, val updatedAt: String?, val environment: String?,
    val pendingCreation: Boolean, val recipientName: String?, val recipientAddress: String,
    val customerShippingFee: Long, val orderStatus: String? = null
)

@Service
class GhnShipmentService(
    private val orders: OrderRepository,
    private val items: OrderItemRepository,
    private val client: GhnClient,
    private val settings: GhnSettings,
    transactionManager: PlatformTransactionManager,
    private val shopService: GhnShopService? = null
) {
    private val tx = TransactionTemplate(transactionManager)
    private val mapper = ObjectMapper()

    fun areas(phone: String, level: String, parent: Int?): List<GhnArea> {
        // Use only the current seller's assigned shop, never expose all platform shops.
        val shop = tx.execute {
            // Ownership/role is enforced by the controller, shop assignment is explicit.
            settings.shopFor(phone)
        }!!
        val (path, id, name) = when (level) {
            "provinces" -> Triple(NEW_PROVINCES, "_id", "name")
            "districts" -> throw ResponseStatusException(HttpStatus.CONFLICT, "App đang dùng địa chỉ GHN cũ. Hãy cập nhật app để chọn tỉnh/thành và phường/xã mới.")
            "wards" -> { require(parent != null && parent > 0); Triple(newWards(parent), "_id", "name") }
            else -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return client.get(path, shop).filter { it.path("status").asInt() == 1 }.map { GhnArea(it.path(id).asText(), it.path(name).asText()) }
    }

    fun view(id: Long, phone: String): GhnShipmentView = tx.execute {
        val order = owned(id, phone, sellerOnly = false)
        viewOf(order)
    }!!

    fun quote(id: Long, phone: String, request: GhnParcelRequest): GhnQuote = tx.execute {
        val order = owned(id, phone)
        requireReady(order)
        require(order.ghnRequestJson == null) { "Đã có yêu cầu GHN. Hãy đối chiếu vận đơn hiện tại." }
        val payload = payload(order, request, settings.shopFor(phone))
        GhnQuote(fee(payload, settings.shopFor(phone)), order.shippingFee,
            if (order.paymentMethod == "COD") money(order.totalAmount) else 0L, settings.environment)
    }!!

    fun create(id: Long, phone: String, request: GhnParcelRequest): GhnShipmentView {
        // Commit the immutable intent BEFORE contacting GHN. A timeout or process crash
        // can then be retried with exactly the same client_order_code and payload.
        tx.executeWithoutResult {
            val order = owned(id, phone)
            if (order.ghnRequestJson == null) {
                requireReady(order, requireCod = true)
                require(order.deliveryMethod != DeliveryMethod.SELF) { "Đơn tự giao chỉ dùng GHN để tham khảo cước, không tạo vận đơn." }
                val shop = settings.shopFor(phone)
                val payload = payload(order, request, shop)
                val quotedFee = fee(payload, shop)
                require(request.expectedFee == quotedFee) { "Cước GHN đã thay đổi. Vui lòng lấy báo giá mới." }
                payload.put("client_order_code", "AG-${UUID.randomUUID()}")
                require(order.deliveryMethod != DeliveryMethod.SELF) { "Hãy chọn Gửi GHN trước khi tạo vận đơn." }
                order.deliveryMethod = DeliveryMethod.GHN
                order.ghnRequestJson = payload.toString()
                order.ghnShopId = shop
                order.ghnEnvironment = settings.environment
                order.ghnStatus = "creating"
                order.ghnShippingFee = quotedFee
                orders.save(order)
            }
        }
        return retry(id, phone)
    }

    fun retry(id: Long, phone: String): GhnShipmentView {
        // Serialize create/retry/release/cancel on the order lock. The immutable
        // intent was committed in create(), independently of this network call.
        val outcome = tx.execute {
            val order = owned(id, phone)
            order.requirePaymentForFulfillment()
            require(!order.isGhnSimulation()) { "Đơn thử nghiệm đã chuyển sang tự cập nhật giao hàng." }
            checkEnvironment(order)
            val requestJson = requireNotNull(order.ghnRequestJson) { "Chưa có yêu cầu tạo vận đơn." }
            if (order.ghnOrderCode != null) return@execute Pair(viewOf(order), null)
            try {
                val result = client.post("/v2/shipping-order/create", order.ghnShopId!!, mapper.readTree(requestJson))
                attach(order, result)
                orders.save(order)
                Pair(viewOf(order), null)
            } catch (e: GhnRejectedException) {
                // Commit the rejection before returning the error to the caller.
                // Keep the reference until GHN confirms there is no waybill.
                order.ghnStatus = "create_rejected"
                order.ghnUpdatedAt = LocalDateTime.now()
                orders.save(order)
                Pair(null, e)
            }
        }!!
        outcome.second?.let { throw it }
        return requireNotNull(outcome.first)
    }

    fun release(id: Long, phone: String): GhnShipmentView = tx.execute {
        val order = owned(id, phone)
        require(!order.isGhnSimulation()) { "Đơn thử nghiệm đã chuyển sang tự cập nhật giao hàng." }
        // Idempotent: a second click must not alter a newer delivery or purchase state.
        if (order.ghnRequestJson == null || order.ghnOrderCode != null) return@execute viewOf(order)
        checkEnvironment(order)
        require(order.status == "CONFIRMED") { "Đơn không còn chờ gửi hàng." }
        val reference = mapper.readTree(order.ghnRequestJson).path("client_order_code").asText()
        require(reference.matches(Regex("[A-Za-z0-9_-]{1,50}"))) { "Thiếu mã đối chiếu yêu cầu GHN." }
        try {
            val data = client.get("/v2/shipping-order/detail-by-client-code?client_order_code=$reference", order.ghnShopId!!)
            require(data.path("client_order_code").asText() == reference &&
                data.path("shop_id").asInt() == order.ghnShopId) { "Thông tin đối chiếu GHN không khớp." }
            attach(order, data, fromLookup = true)
        } catch (e: GhnOrderNotFoundException) {
            require(order.ghnStatus == "create_rejected") {
                "GHN chưa tìm thấy vận đơn nhưng lần gửi trước chưa rõ kết quả. Hãy bấm Thử tạo lại để nhận kết quả xác định trước khi bỏ yêu cầu."
            }
            // The carrier explicitly reports no order under this shop/reference.
            // A network/auth/server error never reaches this branch.
            order.ghnRequestJson = null
            order.ghnShopId = null
            order.ghnEnvironment = null
            order.ghnStatus = "not_created"
            order.ghnShippingFee = null
            order.ghnExpectedDeliveryTime = null
            order.ghnUpdatedAt = LocalDateTime.now()
        }
        orders.save(order)
        viewOf(order)
    }!!

    private fun attach(order: Order, data: JsonNode, fromLookup: Boolean = false) {
        order.requirePaymentForFulfillment()
        val code = data.path("order_code").asText()
        if (!code.matches(Regex("[A-Za-z0-9_-]{1,80}"))) throw ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "GHN chưa trả mã vận đơn hợp lệ. Hãy kiểm tra lại yêu cầu.")
        val status = if (fromLookup) data.path("status").asText() else "ready_to_pick"
        require(status.isNotBlank()) { "GHN chưa trả trạng thái vận đơn." }
        order.deliveryMethod = DeliveryMethod.GHN
        order.ghnOrderCode = code
        order.ghnStatus = status
        if (order.status !in setOf("COMPLETED", "CANCELLED")) {
            if (status == "delivered") order.status = "SHIPPING"
            else if (status in SHIPPING_STATES) order.status = "SHIPPING"
        }
        if (data.hasNonNull("total_fee")) order.ghnShippingFee = data.path("total_fee").asLong()
        order.ghnExpectedDeliveryTime = data.get(if (fromLookup) "leadtime" else "expected_delivery_time")
            ?.takeUnless { it.isNull }?.asText()
        order.ghnUpdatedAt = LocalDateTime.now()
    }

    fun refresh(id: Long, phone: String): GhnShipmentView = tx.execute {
        val order = owned(id, phone, sellerOnly = false)
        require(!order.isGhnSimulation()) { "Đơn mô phỏng do người bán tự cập nhật; không đồng bộ trạng thái GHN." }
        checkEnvironment(order)
        val code = requireNotNull(order.ghnOrderCode) { "Chưa có mã vận đơn. Người bán cần thử lại yêu cầu tạo." }
        val data = client.get("/v2/shipping-order/detail?order_code=$code", order.ghnShopId!!)
        require(data.path("order_code").asText() == code) { "Mã vận đơn GHN không khớp." }
        val status = data.path("status").asText()
        require(status.isNotBlank()) { "GHN chưa trả trạng thái." }
        // Do not regress terminal states or restore stock for a return in transit.
        if (order.ghnStatus !in setOf("delivered", "cancel", "returned")) {
            order.ghnStatus = status
            if (order.status !in setOf("COMPLETED", "CANCELLED") && !order.isAwaitingOnlinePayment()) {
                if (status == "delivered") order.status = "SHIPPING"
                else if (status in SHIPPING_STATES) order.status = "SHIPPING"
            }
        }
        order.ghnExpectedDeliveryTime = data.get("leadtime")?.takeUnless { it.isNull }?.asText()
            ?: order.ghnExpectedDeliveryTime
        order.ghnUpdatedAt = LocalDateTime.now()
        orders.save(order)
        viewOf(order)
    }!!

    fun cancel(id: Long, phone: String): GhnShipmentView = tx.execute {
        val order = owned(id, phone)
        require(!order.isGhnSimulation()) { "Đơn đã chuyển sang tự giao mô phỏng." }
        checkEnvironment(order)
        val code = requireNotNull(order.ghnOrderCode) { "Hãy đối chiếu yêu cầu tạo vận đơn trước khi hủy." }
        require(order.status == "CONFIRMED") { "Chỉ hủy vận đơn trước khi bàn giao hàng. Các trường hợp khác xử lý trên GHN." }
        val body = mapper.createObjectNode().put("reason_code", "GHN-CANCEL-OTHER")
        body.putArray("order_codes").add(code)
        val result = client.post("/v2/switch-status/cancel", order.ghnShopId!!, body)
        require(result.any { it.path("order_code").asText() == code && it.path("result").asBoolean() }) {
            "GHN chưa xác nhận hủy vận đơn. Hãy cập nhật trạng thái và kiểm tra lại."
        }
        order.ghnStatus = "cancel"
        order.ghnUpdatedAt = LocalDateTime.now()
        orders.save(order)
        viewOf(order)
    }!!

    private fun payload(order: Order, request: GhnParcelRequest, shop: Int): com.fasterxml.jackson.databind.node.ObjectNode {
        request.validate()
        val province = client.get(NEW_PROVINCES, shop).firstOrNull {
            it.path("_id").asInt() == request.provinceId && it.path("status").asInt() == 1
        } ?: throw IllegalArgumentException("Tỉnh/thành GHN không hợp lệ hoặc đã ngưng sử dụng. Hãy chọn lại từ danh mục mới.")
        val ward = client.get(newWards(request.provinceId), shop).firstOrNull {
            it.path("_id").asText() == request.wardCode && it.path("status").asInt() == 1 &&
                it.path("parent_id").asInt() == request.provinceId
        } ?: throw IllegalArgumentException("Phường/xã không thuộc tỉnh/thành đã chọn hoặc đã ngưng sử dụng.")
        val orderItems = items.findAllByOrder(order)
        return mapper.createObjectNode().apply {
            shopService?.senderPayload(orderItems.first().product.farmer.phoneNumber, shop)?.let { sender ->
                listOf("from_name", "from_phone", "from_address", "from_province_name", "from_ward_name", "is_new_from_address")
                    .forEach { key -> set<JsonNode>(key, sender.get(key)) }
            }
            put("to_name", requireNotNull(order.receiverName))
            put("to_phone", order.phoneNumber)
            put("to_address", order.shippingAddress)
            put("to_province_name", province.path("name").asText())
            put("to_ward_name", ward.path("name").asText())
            put("is_new_to_address", true)
            put("weight", request.weight); put("length", request.length)
            put("width", request.width); put("height", request.height)
            put("service_type_id", 2)
            // Customer has already agreed to pay goods + local shipping in checkout.
            // Collect that total ONCE; GHN bills the platform's shop for its carrier fee.
            put("payment_type_id", 1)
            val cod = if (order.paymentMethod == "COD") money(order.totalAmount) else 0L
            put("cod_amount", cod)
            put("cod_value", cod)
            put("insurance_value", 0)
            put("required_note", "KHONGCHOXEMHANG")
            put("content", orderItems.joinToString("; ") {
                "${it.product.name}: ${it.quantity} ${it.product.unit}"
            }.take(2000))
        }
    }

    private fun fee(payload: JsonNode, shop: Int): Long {
        val result = client.post("/v2/shipping-order/preview", shop, payload)
        require(result.hasNonNull("total_fee") && result.path("total_fee").canConvertToLong()) { "GHN không trả cước hợp lệ." }
        return result.path("total_fee").asLong().also { require(it >= 0) }
    }

    private fun owned(id: Long, phone: String, sellerOnly: Boolean = true): Order {
        val order = orders.findForStatusUpdate(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.") }
        val sellers = items.findAllByOrder(order).map { it.product.farmer.phoneNumber }.toSet()
        if (sellers.size != 1 || (phone !in sellers && (sellerOnly || phone != order.user.phoneNumber))) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xử lý vận đơn này.")
        }
        return order
    }

    private fun requireReady(order: Order, requireCod: Boolean = false) {
        order.requirePaymentForFulfillment()
        require(order.status == "CONFIRMED") { "Cần xác nhận đơn hàng trước khi gửi GHN." }
        require(!order.receiverName.isNullOrBlank() && (!requireCod || order.paymentMethod == "COD")) {
            "Cần đầy đủ tên người nhận; vận đơn GHN cũ chỉ hỗ trợ COD."
        }
        money(order.totalAmount)
    }

    fun reference(id: Long, phone: String, request: GhnParcelRequest): GhnShipmentView = tx.execute {
        val order = owned(id, phone)
        requireReady(order)
        require(order.ghnRequestJson == null) { "Đơn đã có yêu cầu vận chuyển GHN, cần xử lý vận đơn hiện tại trước." }
        val shop = settings.shopFor(phone)
        val quoted = fee(payload(order, request, shop), shop)
        require(request.expectedFee == quoted) { "Cước GHN đã thay đổi. Hãy lấy báo giá mới." }
        order.deliveryMethod = DeliveryMethod.SELF
        order.ghnShippingFee = quoted
        order.ghnStatus = "quote_only"
        order.ghnEnvironment = settings.environment
        order.ghnUpdatedAt = LocalDateTime.now()
        orders.save(order)
        viewOf(order)
    }!!

    // Explicit opt-in for old staging waybills only; never unlock a production shipment.
    fun simulation(id: Long, phone: String): GhnShipmentView = tx.execute {
        val order = owned(id, phone)
        require(order.ghnEnvironment == "staging" && order.ghnRequestJson != null) {
            "Chỉ chuyển vận đơn thử nghiệm sang tự giao mô phỏng."
        }
        require(order.status in setOf("CONFIRMED", "SHIPPING")) { "Đơn đã đóng, không thể chuyển sang mô phỏng." }
        order.deliveryMethod = DeliveryMethod.SELF
        order.ghnStatus = "simulation"
        order.ghnUpdatedAt = LocalDateTime.now()
        orders.save(order)
        viewOf(order)
    }!!
    private fun checkEnvironment(order: Order) {
        require(order.ghnEnvironment == settings.environment) { "Vận đơn thuộc môi trường GHN khác. Hãy khôi phục cấu hình ban đầu." }
        // Checks credentials are enabled while preserving the shop snapshot on retries.
        settings.shopFor(items.findAllByOrder(order).first().product.farmer.phoneNumber)
    }
    private fun money(value: Double): Long {
        require(value.isFinite() && value > 0 && value <= 50_000_000 && kotlin.math.abs(value - value.roundToLong()) < 0.001) {
            "COD GHN cần tổng tiền nguyên VND, lớn hơn 0 và không quá 50 triệu."
        }
        return value.roundToLong()
    }
    private fun viewOf(order: Order) = GhnShipmentView(order.ghnOrderCode, order.ghnStatus,
        order.ghnShippingFee, order.ghnExpectedDeliveryTime, order.ghnUpdatedAt?.toString(),
        order.ghnEnvironment, order.ghnRequestJson != null && order.ghnOrderCode == null && !order.isGhnSimulation(),
        order.receiverName, order.shippingAddress, order.shippingFee, order.status)

    companion object {
        const val NEW_PROVINCES = "/v3/master-data/province/all?offset=0&limit=200"
        fun newWards(provinceId: Int) = "/v3/master-data/ward/all-by-province-id?province_id=$provinceId&offset=0&limit=200"

        val SHIPPING_STATES = setOf("picked", "storing", "transporting", "sorting", "delivering", "money_collect_delivering",
            "delivery_fail", "waiting_to_return", "return", "return_transporting", "return_sorting", "returning", "return_fail", "returned", "lost", "damage")
    }
}
