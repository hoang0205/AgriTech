package com.uet.agritech.delivery.ghn

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.uet.agritech.user.Role
import com.uet.agritech.user.UserRepository
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Entity
@Table(name = "ghn_seller_shops", uniqueConstraints = [UniqueConstraint(columnNames = ["environment", "shop_id"])])
class GhnSellerShop(
    @Id var id: String = "",
    @Column(nullable = false) var environment: String = "",
    @Column(name = "shop_id") var shopId: Int? = null,
    @Column(columnDefinition = "LONGTEXT") var requestJson: String? = null,
    @Column(columnDefinition = "LONGTEXT") var senderJson: String? = null
)

interface GhnSellerShopRepository : JpaRepository<GhnSellerShop, String>

data class GhnShopView(
    val connected: Boolean, val pending: Boolean, val environment: String,
    val shopId: Int? = null, val name: String? = null, val address: String? = null,
    val outcome: String? = null, val senderAddress: GhnSenderAddress? = null,
    val suggestedPickupAddress: String? = null
)
data class GhnShopRequest(val name: String, val address: String, val provinceId: Int, val districtId: Int, val wardCode: String)
data class GhnSenderRequest(val address: String, val provinceId: Int, val wardCode: String)
data class GhnSenderAddress(val address: String, val provinceId: Int, val wardCode: String, val provinceName: String, val wardName: String)

@Service
class GhnShopService(
    private val settings: GhnSettings, private val client: GhnClient,
    private val links: GhnSellerShopRepository, private val users: UserRepository,
    manager: PlatformTransactionManager
) {
    private val tx = TransactionTemplate(manager)
    private val mapper = ObjectMapper()
    private fun key(phone: String) = "${settings.environment}:$phone"

    fun view(phone: String): GhnShopView {
        settings.requireEnabled()
        val link = links.findById(key(phone)).orElse(null)
        val shopId = settings.configuredShopFor(phone)
        val pickup = users.findByPhoneNumber(phone).orElse(null)?.pickupAddress?.trim()?.takeIf { it.isNotBlank() }
        if (shopId == null) return GhnShopView(false, link?.requestJson != null, settings.environment, suggestedPickupAddress = pickup)
        val shop = allShops().firstOrNull { it.path("_id").asInt() == shopId && it.path("status").asInt() == 1 }
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Shop GHN đã kết nối không còn hoạt động hoặc tài khoản không có quyền truy cập.")
        val sender = link?.takeIf { it.shopId == shopId }?.senderJson?.let(mapper::readTree)?.let {
            GhnSenderAddress(it.path("from_address").asText(), it.path("province_id").asInt(), it.path("ward_code").asText(),
                it.path("from_province_name").asText(), it.path("from_ward_name").asText())
        }
        return GhnShopView(true, false, settings.environment, shopId, shop.path("name").asText(), shop.path("address").asText(),
            senderAddress = sender, suggestedPickupAddress = pickup)
    }

    fun saveSender(phone: String, request: GhnSenderRequest): GhnShopView {
        settings.requireEnabled()
        val shopId = settings.shopFor(phone)
        val shop = view(phone)
        require(request.address.trim().length in 5..500) { "Nhập địa chỉ lấy hàng đầy đủ." }
        val province = client.get(GhnShipmentService.NEW_PROVINCES, shopId).firstOrNull {
            it.path("_id").asInt() == request.provinceId && it.path("status").asInt() == 1
        } ?: throw IllegalArgumentException("Chọn tỉnh/thành lấy hàng từ danh mục mới của GHN.")
        val ward = client.get(GhnShipmentService.newWards(request.provinceId), shopId).firstOrNull {
            it.path("_id").asText() == request.wardCode && it.path("parent_id").asInt() == request.provinceId && it.path("status").asInt() == 1
        } ?: throw IllegalArgumentException("Phường/xã lấy hàng không thuộc tỉnh/thành đã chọn.")
        val sender = mapper.createObjectNode().apply {
            put("province_id", request.provinceId); put("ward_code", request.wardCode)
            put("from_name", shop.name); put("from_phone", phone)
            put("from_address", request.address.trim())
            put("from_province_name", province.path("name").asText())
            put("from_ward_name", ward.path("name").asText())
            put("is_new_from_address", true)
        }
        tx.executeWithoutResult {
            lockSeller(phone)
            require(settings.shopFor(phone) == shopId) { "Shop đã thay đổi. Hãy tải lại kết nối GHN." }
            val link = links.findById(key(phone)).orElse(GhnSellerShop(key(phone), settings.environment))
            link.shopId = shopId; link.senderJson = sender.toString(); links.save(link)
        }
        return view(phone)
    }

    // The shipment intent receives a snapshot; changing setup never rewrites a pending request.
    fun senderPayload(phone: String, shopId: Int): JsonNode? = links.findById(key(phone)).orElse(null)
        ?.takeIf { it.shopId == shopId }?.senderJson?.let(mapper::readTree)

    // Never return other sellers' shops to the app. Only match the authenticated phone.
    fun connect(phone: String): GhnShopView {
        settings.requireEnabled()
        if (settings.configuredShopFor(phone) != null) return view(phone).copy(outcome = "ALREADY_CONNECTED")
        val intent = links.findById(key(phone)).orElse(null)?.requestJson?.let(mapper::readTree)
        val matches = allShops().filter {
            it.path("status").asInt() == 1 && normalizePhone(it.path("phone").asText()) == normalizePhone(phone) &&
                (intent == null || it.path("name").asText() == intent.path("name").asText())
        }
        require(matches.size == 1) {
            if (intent != null) "Chưa đối chiếu được lần tạo shop trước. Hãy kiểm tra lại sau; không gửi lại yêu cầu để tránh tạo trùng."
            else if (matches.isEmpty()) "Chưa có shop GHN dùng số điện thoại đăng nhập của bạn. Bạn có thể tạo cửa hàng bên dưới."
            else "Có nhiều shop cùng số điện thoại. Liên hệ quản trị viên để gán đúng cửa hàng."
        }
        tx.executeWithoutResult {
            lockSeller(phone)
            val link = links.findById(key(phone)).orElse(GhnSellerShop(key(phone), settings.environment))
            if (settings.configuredShopFor(phone) == null) {
                link.shopId = matches.single().path("_id").asInt()
                links.save(link)
            }
        }
        return view(phone).copy(outcome = if (intent == null) "CONNECTED_EXISTING" else "RECOVERED")
    }

    fun register(phone: String, request: GhnShopRequest): GhnShopView {
        settings.requireEnabled()
        if (settings.configuredShopFor(phone) != null) return view(phone).copy(outcome = "ALREADY_CONNECTED")
        if (links.findById(key(phone)).orElse(null)?.requestJson != null) return connect(phone)
        require(request.name.trim().length in 1..100 && request.address.trim().length in 5..500) {
            "Nhập tên cửa hàng và địa chỉ lấy hàng đầy đủ."
        }
        val shops = allShops()
        if (shops.any { normalizePhone(it.path("phone").asText()) == normalizePhone(phone) && it.path("status").asInt() == 1 }) {
            return connect(phone)
        }
        val bootstrap = shops.firstOrNull { it.path("status").asInt() == 1 }?.path("_id")?.asInt()
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản AgriTech cần có một shop GHN hoạt động để tạo thêm cửa hàng.")
        validateArea(request)
        val payload = mapper.createObjectNode().apply {
            // Stable, unique marker permits lookup after a timeout, without replaying register.
            put("name", "${request.name.trim()} [AG-${UUID.randomUUID()}]")
            put("phone", phone); put("address", request.address.trim())
            put("district_id", request.districtId); put("ward_code", request.wardCode)
        }
        val reserved = tx.execute {
            lockSeller(phone)
            val link = links.findById(key(phone)).orElse(GhnSellerShop(key(phone), settings.environment))
            if (settings.configuredShopFor(phone) != null || link.requestJson != null) false
            else { link.requestJson = payload.toString(); links.save(link); true }
        }!!
        if (!reserved) return connect(phone)
        val result = try { client.post("/v2/shop/register", bootstrap, payload) }
        catch (e: GhnRejectedException) {
            tx.executeWithoutResult {
                lockSeller(phone)
                links.findById(key(phone)).orElse(null)?.let { it.requestJson = null; links.save(it) }
            }
            throw e
        }
        val id = result.path("shop_id").asInt()
        require(id > 0) { "Chưa nhận được mã shop. Hãy bấm kiểm tra kết nối để đối chiếu, không tạo lại trên web." }
        tx.executeWithoutResult {
            lockSeller(phone)
            val link = links.findById(key(phone)).orElseThrow()
            link.shopId = id; links.save(link)
        }
        return view(phone).copy(outcome = "CREATED")
    }

    // Shop registration still requires GHN's three-level catalog, unlike order recipients.
    fun areas(level: String, parent: Int?): List<GhnArea> {
        settings.requireEnabled()
        val path: String
        val id: String
        val name: String
        when (level) {
            "provinces" -> { path = "/master-data/province"; id = "ProvinceID"; name = "ProvinceName" }
            "districts" -> { require(parent != null && parent > 0); path = "/master-data/district?province_id=$parent"; id = "DistrictID"; name = "DistrictName" }
            "wards" -> { require(parent != null && parent > 0); path = "/master-data/ward?district_id=$parent"; id = "WardCode"; name = "WardName" }
            else -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return client.get(path, 0).filter { it.path("Status").asInt(1) == 1 }
            .map { GhnArea(it.path(id).asText(), it.path(name).asText()) }
    }

    private fun validateArea(request: GhnShopRequest) {
        require(areas("provinces", null).any { it.id == request.provinceId.toString() } &&
            areas("districts", request.provinceId).any { it.id == request.districtId.toString() } &&
            areas("wards", request.districtId).any { it.id == request.wardCode }) {
            "Khu vực lấy hàng không hợp lệ. Hãy chọn lại tỉnh, huyện và xã theo danh mục đăng ký shop GHN."
        }
    }

    private fun allShops(): List<JsonNode> {
        val result = mutableListOf<JsonNode>()
        var offset = 0
        repeat(100) {
            val page = client.get("/v2/shop/all?offset=$offset&limit=200", 0)
            val shops = page.path("shops")
            require(shops.isArray) { "GHN chưa trả danh sách cửa hàng hợp lệ." }
            if (shops.isEmpty) return result
            result.addAll(shops.toList())
            if (shops.size() < 200) return result
            val next = page.path("last_offset").asInt()
            if (next <= offset) throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Chưa đọc đủ danh sách shop GHN. Thử lại sau.")
            offset = next
        }
        throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Danh sách shop GHN quá lớn để đối chiếu. Liên hệ quản trị viên.")
    }

    private fun lockSeller(phone: String) {
        val user = users.findByPhoneForAuthUpdate(phone).orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED) }
        if (user.role != Role.FARMER) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }
    private fun normalizePhone(value: String) = value.trim().replace(Regex("^\\+?84"), "0")
}
