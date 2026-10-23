package com.uet.agritech.order

import com.uet.agritech.cart.CartItem
import com.uet.agritech.delivery.*
import com.uet.agritech.user.ShippingAddress
import com.uet.agritech.user.ShippingAddressRepository
import com.uet.agritech.cart.CartItemRepository
import com.uet.agritech.notification.NotificationService
import com.uet.agritech.order.dto.CheckoutRequest
import com.uet.agritech.order.dto.OrderStatus
import com.uet.agritech.product.Product
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.review.ReviewRepository
import com.uet.agritech.user.RecommendationService
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import io.mockk.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OrderServiceTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var orderItemRepository: OrderItemRepository
    private lateinit var cartRepository: CartItemRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var userRepository: UserRepository
    private lateinit var entityManager: EntityManager
    private val addresses = mockk<ShippingAddressRepository>()
    private val delivery = mockk<DeliveryQuoteService>()
    private lateinit var service: OrderService

    private lateinit var buyer: User
    private lateinit var seller: User
    private lateinit var product: Product
    private lateinit var order: Order

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        orderItemRepository = mockk()
        cartRepository = mockk()
        productRepository = mockk()
        userRepository = mockk()
        entityManager = mockk(relaxUnitFun = true)

        service = OrderService(
            orderRepository = orderRepository,
            orderItemRepository = orderItemRepository,
            cartItemRepository = cartRepository,
            productRepository = productRepository,
            userRepository = userRepository,
            interactionService = mockk<RecommendationService>(
                relaxUnitFun = true
            ),
            reviewRepository = mockk<ReviewRepository>(),
            notificationService = mockk<NotificationService>(
                relaxUnitFun = true
            ),
            entityManager = entityManager,
            addressRepository = addresses,
            deliveryQuoteService = delivery
        )

        buyer = User(
            id = "buyer-1",
            fullName = "Buyer Test",
            phoneNumber = "0900000001",
            email = "buyer@example.test",
            password = "encoded-password",
            role = Role.BUYER
        )

        seller = User(
            id = "seller-1",
            fullName = "Farmer Test",
            phoneNumber = "0900000002",
            email = "seller@example.test",
            password = "encoded-password",
            role = Role.FARMER
        )

        product = Product(
            id = "product-1",
            name = "Rau test",
            category = "Rau",
            price = 20000.0,
            quantity = 10.0,
            unit = "kg",
            description = "San pham kiem thu",
            farmer = seller
        )

        order = Order(
            id = 100L,
            user = buyer,
            totalAmount = 40000.0,
            shippingAddress = "Dia chi test",
            phoneNumber = buyer.phoneNumber,
            status = "PENDING",
            deliveryMethod = DeliveryMethod.SELF
        )

        every { addresses.findById(1L) } returns Optional.of(ShippingAddress(
            id = 1L, user = buyer, receiverName = "Receiver", phoneNumber = buyer.phoneNumber,
            addressDetail = "Dia chi test", latitude = 21.0, longitude = 105.0
        ))
        every { delivery.quote(any(), any()) } returns DeliveryQuoteResponse(
            addressId = 1L, maxDistanceKm = 30.0,
            sellers = listOf(SellerDeliveryQuote(sellerId = "seller-1", sellerName = "Farmer Test",
                cartItemIds = listOf(1L), status = DeliveryStatus.AVAILABLE,
                message = "OK", shippingFee = 15000)),
            allDeliverable = true, totalShippingFee = 15000
        )

        every {
            userRepository.findByPhoneNumber(buyer.phoneNumber)
        } returns Optional.of(buyer)

        every {
            productRepository.findForStockUpdate("product-1")
        } returns Optional.of(product)

        every {
            productRepository.save(any<Product>())
        } answers {
            firstArg<Product>()
        }

        every {
            orderRepository.findForStatusUpdate(100L)
        } returns Optional.of(order)

        every {
            orderRepository.save(any<Order>())
        } answers {
            val input = firstArg<Order>()

            if (input.id != null) {
                input
            } else {
                Order(
                    id = 100L,
                    user = input.user,
                    totalAmount = input.totalAmount,
                    shippingAddress = input.shippingAddress,
                    phoneNumber = input.phoneNumber,
                    status = input.status,
                    shippingFee = input.shippingFee,
                    receiverName = input.receiverName,
                    paymentMethod = input.paymentMethod
                )
            }
        }

        every {
            orderItemRepository.saveAll(any<List<OrderItem>>())
        } answers {
            firstArg<List<OrderItem>>()
        }

        every {
            cartRepository.deleteAll(any<List<CartItem>>())
        } just Runs

        every {
            orderItemRepository.findAllByOrder(order)
        } returns listOf(
            OrderItem(
                order = order,
                product = product,
                quantity = 2.0,
                price = 20000.0
            )
        )
    }

    @Test
    fun `COD tinh dung tien va tru ton kho`() {
        val item = cartItem(id = 1L, quantity = 2.5)

        every {
            cartRepository.findAllForCheckout(listOf(1L))
        } returns listOf(item)

        val result = service.checkout(
            checkoutRequest(1L),
            buyer.phoneNumber
        )

        assertEquals(1, result.size)
        assertEquals("PENDING", result.single().status)
        assertEquals(65000.0, result.single().totalAmount)
        assertEquals(15000L, result.single().shippingFee)
        assertEquals("Receiver", result.single().receiverName)
        assertEquals("COD", result.single().paymentMethod)
        assertEquals(7.5, product.quantity)

        verify(exactly = 1) {
            productRepository.save(product)
        }
        verify(exactly = 1) {
            cartRepository.deleteAll(listOf(item))
        }
    }

    @Test
    fun `checkout freezes cost per order and only new orders use edited product cost`() {
        every { cartRepository.findAllForCheckout(listOf(1L)) } returns listOf(cartItem(id = 1L, quantity = 1.0))
        val captured = mutableListOf<List<OrderItem>>()
        every { orderItemRepository.saveAll(capture(captured)) } answers { firstArg<List<OrderItem>>() }

        product.costPrice = null
        service.checkout(checkoutRequest(1L), buyer.phoneNumber)
        captured[0].single().order.status = "COMPLETED"
        product.costPrice = 12000.0
        service.checkout(checkoutRequest(1L), buyer.phoneNumber)
        captured[1].single().order.status = "COMPLETED"
        product.costPrice = 15000.0
        service.checkout(checkoutRequest(1L), buyer.phoneNumber)

        assertNull(captured[0].single().costPrice)
        assertEquals(12000.0, captured[1].single().costPrice)
        assertEquals(15000.0, captured[2].single().costPrice)
    }

    @Test
    fun `khong cho checkout khi chua chon san pham`() {
        assertFailsWith<RuntimeException> {
            service.checkout(
                checkoutRequest(),
                buyer.phoneNumber
            )
        }

        verifyNoCheckoutWrites()
    }

    @Test
    fun `stock zero rejects an existing cart item`() {
        product.quantity = 0.0
        val item = cartItem(id = 1L, quantity = 1.0)
        every { cartRepository.findAllForCheckout(listOf(1L)) } returns listOf(item)
        assertFailsWith<RuntimeException> {
            service.checkout(checkoutRequest(1L), buyer.phoneNumber)
        }
        assertEquals(0.0, product.quantity)
        verifyNoCheckoutWrites()
    }

    @Test
    fun `buying remaining stock preserves product at zero`() {
        product.quantity = 2.5
        val item = cartItem(id = 1L, quantity = 2.5)
        every { cartRepository.findAllForCheckout(listOf(1L)) } returns listOf(item)
        service.checkout(checkoutRequest(1L), buyer.phoneNumber)
        assertEquals(0.0, product.quantity)
        assertEquals(false, product.hidden)
        verify(exactly = 1) { productRepository.save(product) }
        verify(exactly = 0) { productRepository.delete(any()) }
    }

    @Test
    fun `khong du ton kho thi khong tao don`() {
        val item = cartItem(id = 1L, quantity = 11.0)

        every {
            cartRepository.findAllForCheckout(listOf(1L))
        } returns listOf(item)

        val error = assertFailsWith<RuntimeException> {
            service.checkout(
                checkoutRequest(1L),
                buyer.phoneNumber
            )
        }

        assertEquals(
            "Không đủ hàng cho sản phẩm: Rau test",
            error.message
        )
        assertEquals(10.0, product.quantity)
        verifyNoCheckoutWrites()
    }

    @Test
    fun `nhieu dong gio cung san pham phai kiem tra tong so luong`() {
        val first = cartItem(id = 1L, quantity = 6.0)
        val second = cartItem(id = 2L, quantity = 6.0)

        every {
            cartRepository.findAllForCheckout(listOf(1L, 2L))
        } returns listOf(first, second)

        val error = assertFailsWith<RuntimeException> {
            service.checkout(
                checkoutRequest(1L, 2L),
                buyer.phoneNumber
            )
        }

        assertEquals(
            "Không đủ hàng cho sản phẩm: Rau test",
            error.message
        )
        assertEquals(10.0, product.quantity)
        verifyNoCheckoutWrites()
    }

    @Test
    fun `thieu mot mon trong gio thi tu choi toan bo checkout`() {
        val item = cartItem(id = 1L, quantity = 2.0)

        every {
            cartRepository.findAllForCheckout(listOf(1L, 2L))
        } returns listOf(item)

        val error = assertFailsWith<RuntimeException> {
            service.checkout(
                checkoutRequest(1L, 2L),
                buyer.phoneNumber
            )
        }

        assertEquals(
            "Giỏ hàng đã thay đổi. Vui lòng tải lại trước khi đặt hàng!",
            error.message
        )
        assertEquals(10.0, product.quantity)
        verifyNoCheckoutWrites()
    }

    @Test
    fun `khong duoc checkout mon trong gio cua nguoi khac`() {
        val otherBuyer = User(
            id = "buyer-2",
            fullName = "Other Buyer",
            phoneNumber = "0900000099",
            email = "other@example.test",
            password = "encoded-password",
            role = Role.BUYER
        )

        val foreignItem = CartItem(
            id = 1L,
            user = otherBuyer,
            product = product,
            quantity = 2.0
        )

        every {
            cartRepository.findAllForCheckout(listOf(1L))
        } returns listOf(foreignItem)

        val error = assertFailsWith<RuntimeException> {
            service.checkout(
                checkoutRequest(1L),
                buyer.phoneNumber
            )
        }

        assertEquals("Giỏ hàng không hợp lệ!", error.message)
        assertEquals(10.0, product.quantity)
        verifyNoCheckoutWrites()
    }

    @Test
    fun `ID gio hang trung khong tinh tien va tru kho hai lan`() {
        val item = cartItem(id = 1L, quantity = 2.0)

        every {
            cartRepository.findAllForCheckout(listOf(1L))
        } returns listOf(item)

        val result = service.checkout(
            checkoutRequest(1L, 1L),
            buyer.phoneNumber
        )

        assertEquals(1, result.size)
        assertEquals(55000.0, result.single().totalAmount)
        assertEquals(8.0, product.quantity)

        verify(exactly = 1) {
            productRepository.save(product)
        }
        verify(exactly = 1) {
            cartRepository.deleteAll(listOf(item))
        }
    }

    @Test
    fun `nguoi ban khong duoc nhay tu cho xac nhan sang hoan tat`() {
        assertFailsWith<RuntimeException> {
            service.updateStatus(
                100L,
                OrderStatus.COMPLETED,
                seller.phoneNumber
            )
        }

        assertEquals("PENDING", order.status)

        verify(exactly = 0) {
            orderRepository.save(any<Order>())
        }
        verify(exactly = 0) {
            productRepository.save(any<Product>())
        }
    }

    @Test
    fun `nguoi ban khac khong duoc xu ly don`() {
        val error = assertFailsWith<RuntimeException> {
            service.updateStatus(
                100L,
                OrderStatus.CONFIRMED,
                "0900000099"
            )
        }

        assertEquals(
            "Bạn không có quyền xử lý đơn hàng này!",
            error.message
        )
        assertEquals("PENDING", order.status)

        verify(exactly = 0) {
            orderRepository.save(any<Order>())
        }
        verify(exactly = 0) {
            productRepository.save(any<Product>())
        }
    }

    @Test
    fun `nguoi ban chuyen trang thai dung quy trinh`() {
        service.updateStatus(
            100L,
            OrderStatus.CONFIRMED,
            seller.phoneNumber
        )
        assertEquals("CONFIRMED", order.status)

        service.updateStatus(
            100L,
            OrderStatus.SHIPPING,
            seller.phoneNumber
        )
        assertEquals("SHIPPING", order.status)

        service.updateStatus(
            100L,
            OrderStatus.COMPLETED,
            seller.phoneNumber
        )
        assertEquals("COMPLETED", order.status)
        assertEquals(10.0, product.quantity)

        verify(exactly = 3) {
            orderRepository.save(order)
        }
        verify(exactly = 0) {
            productRepository.save(any<Product>())
        }
    }

    @Test
    fun `huy don hoan kho va khong hoan kho hai lan`() {
        product.quantity = 8.0

        service.cancelOrderByBuyer(100L, buyer.phoneNumber)

        assertEquals("CANCELLED", order.status)
        assertEquals(10.0, product.quantity)

        assertFailsWith<RuntimeException> {
            service.cancelOrderByBuyer(100L, buyer.phoneNumber)
        }

        assertEquals(10.0, product.quantity)

        verify(exactly = 1) {
            productRepository.save(product)
        }
        verify(exactly = 1) {
            orderRepository.save(order)
        }
    }

    @Test
    fun `huy boi nguoi ban roi nguoi mua khong hoan ton hai lan`() {
        product.quantity = 8.0

        service.updateStatus(
            100L,
            OrderStatus.CANCELLED,
            seller.phoneNumber
        )

        assertEquals("CANCELLED", order.status)
        assertEquals(10.0, product.quantity)

        assertFailsWith<RuntimeException> {
            service.cancelOrderByBuyer(100L, buyer.phoneNumber)
        }

        assertEquals(10.0, product.quantity)

        verify(exactly = 1) {
            productRepository.save(product)
        }
        verify(exactly = 1) {
            orderRepository.save(order)
        }
    }

    @Test
    fun `nguoi mua khac khong duoc huy don`() {
        product.quantity = 8.0

        val error = assertFailsWith<RuntimeException> {
            service.cancelOrderByBuyer(
                100L,
                "0900000099"
            )
        }

        assertEquals(
            "Bạn không có quyền hủy đơn hàng này!",
            error.message
        )
        assertEquals("PENDING", order.status)
        assertEquals(8.0, product.quantity)

        verify(exactly = 0) {
            orderRepository.save(any<Order>())
        }
        verify(exactly = 0) {
            productRepository.save(any<Product>())
        }
    }

    @Test
    fun `nguoi mua khong duoc huy don da xac nhan`() {
        order.status = "CONFIRMED"
        product.quantity = 8.0

        assertFailsWith<RuntimeException> {
            service.cancelOrderByBuyer(
                100L,
                buyer.phoneNumber
            )
        }

        assertEquals("CONFIRMED", order.status)
        assertEquals(8.0, product.quantity)

        verify(exactly = 0) {
            orderRepository.save(any<Order>())
        }
        verify(exactly = 0) {
            productRepository.save(any<Product>())
        }
    }

    @Test
    fun `manual delivery and cancellation blocked while GHN intent is unresolved`() {
        order.status = "CONFIRMED"
        order.ghnRequestJson = "{}"
        order.ghnStatus = "creating"
        assertFailsWith<IllegalArgumentException> {
            service.updateStatus(100L, OrderStatus.SHIPPING, seller.phoneNumber)
        }
        assertFailsWith<IllegalArgumentException> {
            service.updateStatus(100L, OrderStatus.CANCELLED, seller.phoneNumber)
        }
        assertEquals("CONFIRMED", order.status)
        verify(exactly = 0) { orderRepository.save(any()) }
    }

    @Test
    fun `seller must choose self delivery before manual handover`() {
        order.status = "CONFIRMED"
        order.deliveryMethod = null
        assertFailsWith<IllegalArgumentException> {
            service.updateStatus(100L, OrderStatus.SHIPPING, seller.phoneNumber)
        }
        order.deliveryMethod = DeliveryMethod.GHN
        assertFailsWith<IllegalArgumentException> {
            service.updateStatus(100L, OrderStatus.SHIPPING, seller.phoneNumber)
        }
        order.deliveryMethod = DeliveryMethod.SELF
        service.updateStatus(100L, OrderStatus.SHIPPING, seller.phoneNumber)
        assertEquals("SHIPPING", order.status)
    }

    @Test
    fun `seller can complete a reference quote order or an explicitly opted in staging simulation`() {
        for (simulation in listOf(false, true)) {
            order.status = "CONFIRMED"
            order.deliveryMethod = DeliveryMethod.SELF
            order.ghnStatus = if (simulation) "simulation" else "quote_only"
            order.ghnEnvironment = "staging"
            order.ghnRequestJson = if (simulation) "{}" else null
            order.ghnShippingFee = 64900
            service.updateStatus(100L, OrderStatus.SHIPPING, seller.phoneNumber)
            service.updateStatus(100L, OrderStatus.COMPLETED, seller.phoneNumber)
            assertEquals("COMPLETED", order.status)
            assertEquals(64900L, order.ghnShippingFee)
            assertNull(order.actualShippingCost)
        }
    }

    @Test
    fun `buyer sees chosen delivery method and GHN tracking on their order`() {
        order.deliveryMethod = DeliveryMethod.GHN
        order.ghnOrderCode = "TRACK123"
        order.ghnStatus = "ready_to_pick"
        every { orderRepository.findAllByUserOrderByOrderDateDesc(buyer) } returns listOf(order)
        every { orderItemRepository.findAllByOrder(order) } returns emptyList()
        val response = service.getMyOrders(buyer.phoneNumber).single()
        assertEquals("GHN", response.deliveryMethod)
        assertEquals("TRACK123", response.ghnOrderCode)
    }

    private fun cartItem(id: Long, quantity: Double) =
        CartItem(
            id = id,
            user = buyer,
            product = product,
            quantity = quantity
        )

    private fun checkoutRequest(vararg ids: Long) =
        CheckoutRequest(
            shippingAddress = "Dia chi test",
            phoneNumber = buyer.phoneNumber,
            selectedCartItemIds = ids.toList(),
            paymentMethod = "COD",
            addressId = 1L,
            expectedShippingFee = 15000
        )

    @Test
    fun `seller orders include saved recipient separate from buyer account`() {
        val recipientOrder = Order(
            id = 101L, user = buyer, totalAmount = 40000.0,
            shippingAddress = "Saved delivery address", phoneNumber = "0900000099",
            receiverName = "Different recipient"
        )
        val item = OrderItem(order = recipientOrder, product = product, quantity = 2.0, price = 20000.0)
        every { userRepository.findByPhoneNumber(seller.phoneNumber) } returns Optional.of(seller)
        every { orderItemRepository.findAllByProductFarmer(seller) } returns listOf(item)

        val response = service.getOrdersForFarmer(seller.phoneNumber).single()
        assertEquals("Different recipient", response.receiverName)
        assertEquals("0900000099", response.buyerPhone)
        assertEquals("Saved delivery address", response.shippingAddress)
        assertEquals(buyer.fullName, response.buyerName)
    }

    private fun verifyNoCheckoutWrites() {
        verify(exactly = 0) {
            orderRepository.save(any<Order>())
        }
        verify(exactly = 0) {
            productRepository.save(any<Product>())
        }
        verify(exactly = 0) {
            cartRepository.deleteAll(any<List<CartItem>>())
        }
    }
}
