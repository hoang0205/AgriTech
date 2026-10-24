package com.uet.agritech.delivery.ghn

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.uet.agritech.order.*
import com.uet.agritech.product.Product
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.*

class GhnShipmentServiceTest {
    private val mapper = ObjectMapper()
    private lateinit var orders: OrderRepository
    private lateinit var items: OrderItemRepository
    private lateinit var client: GhnClient
    private lateinit var service: GhnShipmentService
    private lateinit var order: Order
    private lateinit var manager: PlatformTransactionManager
    private val parcel = GhnParcelRequest(202, 0, "21007", 1500, 30, 20, 10, 30000)

    @BeforeEach
    fun setup() {
        orders = mockk(); items = mockk(); client = mockk(); manager = mockk(relaxed = true)
        every { manager.getTransaction(any()) } answers { mockk<TransactionStatus>(relaxed = true) }
        val buyer = User(id = "buyer", fullName = "Buyer", phoneNumber = "0900000001", email = "b@test.vn", password = "x", role = Role.BUYER)
        val seller = User(id = "seller", fullName = "Seller", phoneNumber = "0900000002", email = "s@test.vn", password = "x", role = Role.FARMER)
        val product = Product(id = "p", name = "Rau", category = "Rau", price = 100000.0, quantity = 10.0, unit = "kg", description = "Rau", farmer = seller)
        order = Order(id = 1, user = buyer, totalAmount = 225000.0, shippingAddress = "12 Duong A", phoneNumber = "0900000003",
            status = "CONFIRMED", shippingFee = 25000, receiverName = "Nguoi nhan khac", paymentMethod = "COD")
        every { orders.findForStatusUpdate(1) } returns Optional.of(order)
        every { orders.save(any()) } answers { firstArg() }
        every { items.findAllByOrder(order) } returns listOf(OrderItem(order = order, product = product, quantity = 2.0, price = 100000.0))
        every { client.get(GhnShipmentService.NEW_PROVINCES, 123) } returns json("""[{"_id":202,"name":"HCM","status":1}]""")
        every { client.get(GhnShipmentService.newWards(202), 123) } returns json("""[{"_id":21007,"name":"Phuong 1","status":1,"parent_id":202}]""")
        every { client.post("/v2/shipping-order/preview", 123, any()) } returns json("""{"total_fee":30000}""")
        every { client.post("/v2/shipping-order/create", 123, any()) } returns json("""{"order_code":"TEST123","total_fee":30000}""")
        service = GhnShipmentService(orders, items, client, GhnSettings(true, "test-token", "staging", "0900000002=123"), manager)
    }

    @Test
    fun `COD includes customer shipping once and shop pays carrier fee`() {
        val body = slot<JsonNode>()
        every { client.post("/v2/shipping-order/create", 123, capture(body)) } returns json("""{"order_code":"TEST123","total_fee":30000}""")
        service.create(1, "0900000002", parcel)
        assertEquals(225000L, body.captured.path("cod_amount").asLong())
        assertEquals(1, body.captured.path("payment_type_id").asInt())
        assertEquals("Nguoi nhan khac", body.captured.path("to_name").asText())
        assertEquals("0900000003", body.captured.path("to_phone").asText())
        assertTrue(body.captured.path("is_new_to_address").asBoolean())
        assertEquals("HCM", body.captured.path("to_province_name").asText())
        assertEquals("Phuong 1", body.captured.path("to_ward_name").asText())
        assertFalse(body.captured.has("to_ward_code"))
        assertFalse(body.captured.has("to_district_id"))
        assertFalse(body.captured.has("to_district_name"))
        assertEquals(25000L, order.shippingFee)
        assertEquals(30000L, order.ghnShippingFee)
        assertEquals("CONFIRMED", order.status) // creating a waybill is not handover
    }

    @Test
    fun `confirmed sender address is sent explicitly and frozen across shipment retries`() {
        val shops = mockk<GhnShopService>()
        every { shops.senderPayload("0900000002", 123) } returns json("""{"from_name":"Seller","from_phone":"0900000002","from_address":"Xom 9","from_province_name":"Ninh Binh","from_ward_name":"Xa A","is_new_from_address":true}""")
        service = GhnShipmentService(orders, items, client, GhnSettings(true, "test-token", "staging", "0900000002=123"), manager, shops)
        val bodies = mutableListOf<JsonNode>()
        every { client.post("/v2/shipping-order/create", 123, capture(bodies)) } throws
            ResponseStatusException(HttpStatus.BAD_GATEWAY) andThen json("""{"order_code":"TEST123"}""")
        assertFailsWith<ResponseStatusException> { service.create(1, "0900000002", parcel) }
        assertEquals("Xom 9", bodies.first().path("from_address").asText())
        assertEquals("Xa A", bodies.first().path("from_ward_name").asText())
        assertTrue(bodies.first().path("is_new_from_address").asBoolean())
        every { shops.senderPayload("0900000002", 123) } returns json("""{"from_address":"Changed"}""")
        service.retry(1, "0900000002")
        assertEquals(bodies.first(), bodies.last())
        assertEquals(25000L, order.shippingFee)
        assertNull(order.actualShippingCost)
    }

    @Test
    fun `timeout leaves committed intent and retry reuses exact payload`() {
        val bodies = mutableListOf<JsonNode>()
        every { client.post("/v2/shipping-order/create", 123, capture(bodies)) } throws
            ResponseStatusException(HttpStatus.BAD_GATEWAY) andThen json("""{"order_code":"TEST123"}""")
        assertFailsWith<ResponseStatusException> { service.create(1, "0900000002", parcel) }
        verifyOrder { orders.save(order); manager.commit(any()); client.post("/v2/shipping-order/create", 123, any()) }
        assertTrue(service.view(1, "0900000002").pendingCreation)
        service.retry(1, "0900000002")
        assertEquals(bodies[0], bodies[1])
        assertTrue(bodies[0].path("client_order_code").asText().startsWith("AG-"))
        assertEquals("TEST123", order.ghnOrderCode)
    }

    @Test
    fun `repeated create with changed form does not create or charge twice`() {
        service.create(1, "0900000002", parcel)
        service.create(1, "0900000002", parcel.copy(weight = 5000))
        verify(exactly = 1) { client.post("/v2/shipping-order/create", any(), any()) }
        verify(exactly = 1) { client.post("/v2/shipping-order/preview", any(), any()) }
    }

    @Test
    fun `changed price requires a new seller approval before persisting intent`() {
        assertFailsWith<IllegalArgumentException> { service.create(1, "0900000002", parcel.copy(expectedFee = 20000)) }
        assertNull(order.ghnRequestJson)
        verify(exactly = 0) { client.post("/v2/shipping-order/create", any(), any()) }
    }

    @Test
    fun `other seller and buyer cannot create or cancel a shipment`() {
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> { service.create(1, "stranger", parcel) }.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> { service.cancel(1, "0900000001") }.statusCode)
        verify { client wasNot Called }
    }

    @Test
    fun `buyer may refresh delivered shipment but stale updates do not regress completion`() {
        service.create(1, "0900000002", parcel)
        every { client.get("/v2/shipping-order/detail?order_code=TEST123", 123) } returns
            json("""{"order_code":"TEST123","status":"delivered"}""") andThen
            json("""{"order_code":"TEST123","status":"transporting"}""")
        service.refresh(1, "0900000001")
        service.refresh(1, "0900000001")
        assertEquals("COMPLETED", order.status)
        assertEquals("delivered", order.ghnStatus)
    }

    @Test
    fun `GHN cancel refusal must preserve active shipment`() {
        service.create(1, "0900000002", parcel)
        every { client.post("/v2/switch-status/cancel", 123, any()) } returns json("""[{"order_code":"TEST123","result":false}]""")
        assertFailsWith<IllegalArgumentException> { service.cancel(1, "0900000002") }
        assertEquals("ready_to_pick", order.ghnStatus)
        assertEquals("CONFIRMED", order.status)
    }

    @Test
    fun `confirmed GHN cancellation keeps purchase open`() {
        service.create(1, "0900000002", parcel)
        every { client.post("/v2/switch-status/cancel", 123, any()) } returns json("""[{"order_code":"TEST123","result":true}]""")
        service.cancel(1, "0900000002")
        assertEquals("cancel", order.ghnStatus)
        assertEquals("CONFIRMED", order.status)
    }

    @Test
    fun `oversize and mismatched administrative address never create a shipment`() {
        assertFailsWith<IllegalArgumentException> { service.create(1, "0900000002", parcel.copy(weight = 20000)) }
        assertFailsWith<IllegalArgumentException> { service.create(1, "0900000002", parcel.copy(districtId = 999)) }
        verify(exactly = 0) { client.post("/v2/shipping-order/create", any(), any()) }
    }

    @Test
    fun `disabled GHN reports configuration missing without network calls`() {
        assertFailsWith<ResponseStatusException> { GhnSettings(false, "", "staging", "").shopFor("0900000002") }
        verify { client wasNot Called }
    }

    @Test
    fun `self delivery selection must change before GHN can prepare a waybill`() {
        order.deliveryMethod = DeliveryMethod.SELF
        assertFailsWith<IllegalArgumentException> { service.create(1, "0900000002", parcel) }
        assertNull(order.ghnRequestJson)
        verify { client wasNot Called }
    }

    private fun json(value: String) = mapper.readTree(value)

    @Test
    fun `new address quote uses preview and never creates a waybill`() {
        val quote = service.quote(1, "0900000002", parcel)
        assertEquals(30000L, quote.fee)
        verify(exactly = 1) { client.post("/v2/shipping-order/preview", 123, any()) }
        verify(exactly = 0) { client.post("/v2/shipping-order/create", any(), any()) }
        assertNull(order.ghnRequestJson)
    }

    @Test
    fun `reference quote keeps customer price and actual expense separate without creating a shipment`() {
        order.deliveryMethod = DeliveryMethod.SELF
        assertEquals(30000L, service.quote(1, "0900000002", parcel).fee)
        val result = service.reference(1, "0900000002", parcel)
        assertEquals("quote_only", result.status)
        assertEquals(DeliveryMethod.SELF, order.deliveryMethod)
        assertEquals(30000L, order.ghnShippingFee)
        assertEquals(25000L, order.shippingFee)
        assertEquals(225000.0, order.totalAmount)
        assertNull(order.actualShippingCost)
        assertNull(order.ghnRequestJson)
        assertNull(order.ghnOrderCode)
        verify(exactly = 0) { client.post("/v2/shipping-order/create", any(), any()) }
    }

    @Test
    fun `reference price changes must be approved again`() {
        assertFailsWith<IllegalArgumentException> { service.reference(1, "0900000002", parcel.copy(expectedFee = 25000)) }
        assertNull(order.ghnShippingFee)
        assertNull(order.ghnRequestJson)
    }

    @Test
    fun `staging waybill can switch to manual simulation without contacting GHN or later regressing its state`() {
        order.ghnEnvironment = "staging"
        order.ghnRequestJson = "{}"
        order.ghnOrderCode = "OLD-TEST"
        order.ghnStatus = "ready_to_pick"
        service.simulation(1, "0900000002")
        assertTrue(order.isGhnSimulation())
        assertFalse(order.hasActiveGhnShipment())
        order.status = "COMPLETED"
        assertFailsWith<IllegalArgumentException> { service.refresh(1, "0900000002") }
        assertFailsWith<IllegalArgumentException> { service.retry(1, "0900000002") }
        assertEquals("COMPLETED", order.status)
        verify { client wasNot Called }
    }

    @Test
    fun `production waybill cannot switch to simulation`() {
        order.ghnEnvironment = "production"
        order.ghnRequestJson = "{}"
        order.ghnStatus = "ready_to_pick"
        assertFailsWith<IllegalArgumentException> { service.simulation(1, "0900000002") }
        assertTrue(order.hasActiveGhnShipment())
        verify { client wasNot Called }
    }

    @Test
    fun `two level catalog returns only active wards of the selected province`() {
        assertEquals(listOf(GhnArea("202", "HCM")), service.areas("0900000002", "provinces", null))
        assertEquals(listOf(GhnArea("21007", "Phuong 1")), service.areas("0900000002", "wards", 202))
        assertFailsWith<ResponseStatusException> { service.areas("0900000002", "districts", 202) }
    }

    @Test
    fun `inactive or mismatched new wards are rejected before a waybill is created`() {
        every { client.get(GhnShipmentService.newWards(202), 123) } returns json(
            """[{"_id":21007,"name":"Wrong ward","status":1,"parent_id":999}]""")
        assertFailsWith<IllegalArgumentException> { service.create(1, "0900000002", parcel) }
        every { client.get(GhnShipmentService.newWards(202), 123) } returns json(
            """[{"_id":21007,"name":"Inactive ward","status":2,"parent_id":202}]""")
        assertFailsWith<IllegalArgumentException> { service.create(1, "0900000002", parcel) }
        verify(exactly = 0) { client.post("/v2/shipping-order/create", any(), any()) }
    }

    private fun pendingReference(): String = mapper.readTree(order.ghnRequestJson).path("client_order_code").asText()

    @Test
    fun `rejected create is committed and absent waybill can be released without cancelling purchase`() {
        every { client.post("/v2/shipping-order/create", 123, any()) } throws GhnRejectedException("GHN từ chối thông tin")
        assertFailsWith<GhnRejectedException> { service.create(1, "0900000002", parcel) }
        assertEquals("create_rejected", order.ghnStatus)
        assertTrue(service.view(1, "0900000002").pendingCreation)
        val reference = pendingReference()
        every { client.get("/v2/shipping-order/detail-by-client-code?client_order_code=$reference", 123) } throws GhnOrderNotFoundException()
        val result = service.release(1, "0900000002")
        assertFalse(result.pendingCreation)
        assertNull(order.ghnRequestJson)
        assertNull(order.ghnShippingFee)
        assertEquals("not_created", order.ghnStatus)
        assertEquals("CONFIRMED", order.status)
        assertEquals(25000L, order.shippingFee)
        // Repeated release is harmless and does not call GHN again.
        service.release(1, "0900000002")
        verify(exactly = 1) { client.get("/v2/shipping-order/detail-by-client-code?client_order_code=$reference", 123) }
    }

    @Test
    fun `old unresolved request recovers waybill instead of unlocking manual delivery`() {
        every { client.post("/v2/shipping-order/create", 123, any()) } throws ResponseStatusException(HttpStatus.BAD_GATEWAY)
        assertFailsWith<ResponseStatusException> { service.create(1, "0900000002", parcel) }
        val reference = pendingReference()
        every { client.get("/v2/shipping-order/detail-by-client-code?client_order_code=$reference", 123) } returns
            json("""{"order_code":"FOUND123","client_order_code":"$reference","shop_id":123,"status":"transporting"}""")
        val result = service.release(1, "0900000002")
        assertEquals("FOUND123", result.orderCode)
        assertEquals("SHIPPING", order.status)
        assertNotNull(order.ghnRequestJson)
        assertFalse(result.pendingCreation)
    }

    @Test
    fun `network or authorization failure while checking never unlocks unresolved request`() {
        every { client.post("/v2/shipping-order/create", 123, any()) } throws ResponseStatusException(HttpStatus.BAD_GATEWAY)
        assertFailsWith<ResponseStatusException> { service.create(1, "0900000002", parcel) }
        val reference = pendingReference()
        every { client.get("/v2/shipping-order/detail-by-client-code?client_order_code=$reference", 123) } throws ResponseStatusException(HttpStatus.BAD_GATEWAY)
        assertFailsWith<ResponseStatusException> { service.release(1, "0900000002") }
        assertNotNull(order.ghnRequestJson)
        assertTrue(service.view(1, "0900000002").pendingCreation)
    }

    @Test
    fun `other buyer and seller cannot release pending intent`() {
        assertFailsWith<ResponseStatusException> { service.release(1, "stranger") }
        assertFailsWith<ResponseStatusException> { service.release(1, "0900000001") }
        verify { client wasNot Called }
    }

    @Test
    fun `lookup result for another shop cannot release or attach a shipment`() {
        every { client.post("/v2/shipping-order/create", 123, any()) } throws ResponseStatusException(HttpStatus.BAD_GATEWAY)
        assertFailsWith<ResponseStatusException> { service.create(1, "0900000002", parcel) }
        val reference = pendingReference()
        every { client.get("/v2/shipping-order/detail-by-client-code?client_order_code=$reference", 123) } returns
            json("""{"order_code":"WRONG123","client_order_code":"$reference","shop_id":999,"status":"ready_to_pick"}""")
        assertFailsWith<IllegalArgumentException> { service.release(1, "0900000002") }
        assertNull(order.ghnOrderCode)
        assertNotNull(order.ghnRequestJson)
    }

    @Test
    fun `absence after a timeout alone cannot release a possibly in flight create`() {
        every { client.post("/v2/shipping-order/create", 123, any()) } throws ResponseStatusException(HttpStatus.BAD_GATEWAY)
        assertFailsWith<ResponseStatusException> { service.create(1, "0900000002", parcel) }
        val reference = pendingReference()
        every { client.get("/v2/shipping-order/detail-by-client-code?client_order_code=$reference", 123) } throws GhnOrderNotFoundException()
        assertFailsWith<IllegalArgumentException> { service.release(1, "0900000002") }
        assertNotNull(order.ghnRequestJson)
        assertEquals("creating", order.ghnStatus)
    }
}
