package com.uet.agritech.delivery.ghn

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.*

class GhnShopServiceTest {
    private val mapper = ObjectMapper()
    private val phone = "0900000002"
    private val stored = mutableMapOf<String, GhnSellerShop>()
    private lateinit var links: GhnSellerShopRepository
    private lateinit var client: GhnClient
    private lateinit var service: GhnShopService
    private lateinit var settings: GhnSettings
    private lateinit var seller: User
    private var shops: JsonNode = mapper.readTree("""[{"_id":123,"name":"Platform","phone":"0900000000","address":"Platform address","status":1}]""")
    private val request = GhnShopRequest("Seller", "12 Road A", 202, 1452, "21007")

    @BeforeEach fun setup() {
        stored.clear()
        links = mockk(); client = mockk()
        val users = mockk<UserRepository>()
        seller = User(fullName = "Seller", phoneNumber = phone, email = "s@test.vn", password = "x", role = Role.FARMER,
            pickupAddress = "Xóm 9, Hải Hưng, Ninh Bình")
        every { users.findByPhoneNumber(phone) } returns Optional.of(seller)
        val manager = mockk<PlatformTransactionManager>(relaxed = true)
        every { manager.getTransaction(any()) } answers { mockk<TransactionStatus>(relaxed = true) }
        every { links.findById(any()) } answers { Optional.ofNullable(stored[firstArg<String>()]) }
        every { links.save(any()) } answers { firstArg<GhnSellerShop>().also { stored[it.id] = it } }
        every { users.findByPhoneForAuthUpdate(phone) } returns Optional.of(
            User(fullName = "Seller", phoneNumber = phone, email = "s@test.vn", password = "x", role = Role.FARMER))
        every { client.get("/v2/shop/all?offset=0&limit=200", 0) } answers {
            mapper.createObjectNode().set<JsonNode>("shops", shops)
        }
        every { client.get("/master-data/province", 0) } returns mapper.readTree("""[{"ProvinceID":202,"ProvinceName":"Province"}]""")
        every { client.get("/master-data/district?province_id=202", 0) } returns mapper.readTree("""[{"DistrictID":1452,"DistrictName":"District"}]""")
        every { client.get("/master-data/ward?district_id=1452", 0) } returns mapper.readTree("""[{"WardCode":"21007","WardName":"Ward"}]""")
        settings = GhnSettings(true, "test-token", "staging", "", links)
        service = GhnShopService(settings, client, links, users, manager)
    }

    @Test fun `registration saves intent before network and links returned shop`() {
        val payload = slot<JsonNode>()
        every { client.post("/v2/shop/register", 123, capture(payload)) } answers {
            assertNotNull(stored["staging:$phone"]?.requestJson)
            shops = mapper.createArrayNode().addObject().apply {
                put("_id", 456); put("name", payload.captured.path("name").asText())
                put("phone", phone); put("address", "12 Road A"); put("status", 1)
            }.let { mapper.createArrayNode().add(it) }
            mapper.readTree("""{"shop_id":456}""")
        }
        val result = service.register(phone, request)
        assertTrue(result.connected)
        assertEquals("CREATED", result.outcome)
        assertEquals(456, settings.shopFor(phone))
        assertEquals(phone, payload.captured.path("phone").asText())
        assertEquals("12 Road A", result.address)
        assertTrue(payload.captured.path("name").asText().startsWith("Seller [AG-"))
        assertEquals("ALREADY_CONNECTED", service.register(phone, request).outcome)
        verify(exactly = 1) { client.post("/v2/shop/register", any(), any()) }
    }

    @Test fun `timeout can only reconcile and never replays registration`() {
        every { client.post("/v2/shop/register", 123, any()) } throws ResponseStatusException(HttpStatus.BAD_GATEWAY)
        assertFailsWith<ResponseStatusException> { service.register(phone, request) }
        assertTrue(service.view(phone).pending)
        assertFailsWith<IllegalArgumentException> { service.register(phone, request) }
        verify(exactly = 1) { client.post("/v2/shop/register", any(), any()) }
        val intent = mapper.readTree(stored["staging:$phone"]!!.requestJson)
        shops = mapper.readTree("""[{"_id":456,"phone":"$phone","name":${mapper.writeValueAsString(intent.path("name").asText())},"address":"12 Road A","status":1}]""")
        assertEquals("RECOVERED", service.connect(phone).outcome)
        assertEquals(456, settings.shopFor(phone))
    }

    @Test fun `seller cannot connect another phone's shop or an ambiguous shop`() {
        assertFailsWith<IllegalArgumentException> { service.connect(phone) }
        assertNull(settings.configuredShopFor(phone))
        shops = mapper.readTree("""[{"_id":4,"phone":"$phone","status":1},{"_id":5,"phone":"$phone","status":1}]""")
        assertFailsWith<IllegalArgumentException> { service.connect(phone) }
        assertNull(settings.configuredShopFor(phone))
    }

    @Test fun `register reports reuse of existing phone shop without calling registration`() {
        shops = mapper.readTree("""[{"_id":456,"phone":"$phone","name":"Existing","address":"Old address","status":1}]""")
        val result = service.register(phone, request)
        assertEquals("CONNECTED_EXISTING", result.outcome)
        assertEquals(456, result.shopId)
        assertEquals("Old address", result.address)
        verify(exactly = 0) { client.post("/v2/shop/register", any(), any()) }
    }

    @Test fun `seller confirms current pickup area and its payload is scoped to the assigned shop`() {
        stored["staging:$phone"] = GhnSellerShop("staging:$phone", "staging", 123)
        every { client.get(GhnShipmentService.NEW_PROVINCES, 123) } returns mapper.readTree("""[{"_id":1000016,"name":"Tỉnh Ninh Bình","status":1}]""")
        every { client.get(GhnShipmentService.newWards(1000016), 123) } returns mapper.readTree("""[{"_id":321,"name":"Xã A","parent_id":1000016,"status":1}]""")
        val result = service.saveSender(phone, GhnSenderRequest("Xóm 9, địa chỉ lấy hàng", 1000016, "321"))
        assertEquals("Tỉnh Ninh Bình", result.senderAddress?.provinceName)
        val body = service.senderPayload(phone, 123)!!
        assertTrue(body.path("is_new_from_address").asBoolean())
        assertEquals("Xã A", body.path("from_ward_name").asText())
        assertEquals(phone, body.path("from_phone").asText())
        assertNull(service.senderPayload(phone, 999))
        assertNull(service.senderPayload("0900000009", 123))
        verify(exactly = 0) { client.post(any(), any(), any()) }
    }

    @Test fun `pickup ward outside chosen province is rejected without changing saved setup`() {
        stored["staging:$phone"] = GhnSellerShop("staging:$phone", "staging", 123)
        every { client.get(GhnShipmentService.NEW_PROVINCES, 123) } returns mapper.readTree("""[{"_id":202,"name":"Province","status":1}]""")
        every { client.get(GhnShipmentService.newWards(202), 123) } returns mapper.readTree("""[{"_id":321,"name":"Ward","parent_id":999,"status":1}]""")
        assertFailsWith<IllegalArgumentException> { service.saveSender(phone, GhnSenderRequest("12 Road A", 202, "321")) }
        assertNull(stored["staging:$phone"]?.senderJson)
    }

    @Test fun `shop view suggests seller pickup address while retaining the GHN address`() {
        stored["staging:$phone"] = GhnSellerShop("staging:$phone", "staging", 123)
        val result = service.view(phone)
        assertEquals("Xóm 9, Hải Hưng, Ninh Bình", result.suggestedPickupAddress)
        assertEquals("Platform address", result.address)
        seller.pickupAddress = "   "
        assertNull(service.view(phone).suggestedPickupAddress)
    }

    @Test fun `shop assignments are separated by environment and retain existing configuration`() {
        stored["staging:$phone"] = GhnSellerShop("staging:$phone", "staging", 456)
        assertEquals(456, settings.shopFor(phone))
        val production = GhnSettings(true, "test", "production", "", links)
        assertFailsWith<ResponseStatusException> { production.shopFor(phone) }
        assertEquals(789, GhnSettings(true, "test", "production", "$phone=789", links).shopFor(phone))
    }

    @Test fun `explicit rejection allows correction without leaving an unresolved intent`() {
        every { client.post("/v2/shop/register", 123, any()) } throws GhnRejectedException("Rejected")
        assertFailsWith<GhnRejectedException> { service.register(phone, request) }
        assertFalse(service.view(phone).pending)
        assertNull(settings.configuredShopFor(phone))
    }
}
