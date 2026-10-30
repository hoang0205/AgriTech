package com.uet.agritech.order

import com.uet.agritech.cart.CartItem
import com.uet.agritech.cart.CartItemRepository
import com.uet.agritech.delivery.*
import com.uet.agritech.notification.NotificationService
import com.uet.agritech.order.dto.CheckoutRequest
import com.uet.agritech.order.dto.OrderStatus
import com.uet.agritech.product.Product
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.review.ReviewRepository
import com.uet.agritech.user.*
import io.mockk.every
import io.mockk.mockk
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.sql.DriverManager
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.*

/** Opt-in: actual MySQL transactions, exclusively in a fresh randomly named database. */
@EnabledIfEnvironmentVariable(named = "AGRITECH_RUN_DB_CONCURRENCY_TEST", matches = "true")
class OrderConcurrencyIntegrationTest {
    companion object {
        private val schema = "agritech_concurrency_" + UUID.randomUUID().toString().replace("-", "")
        private lateinit var serverUrl: String
        private lateinit var user: String
        private lateinit var password: String
        private var created = false
        private lateinit var context: AnnotationConfigApplicationContext
        lateinit var testUrl: String
            private set

        @JvmStatic @BeforeAll
        fun startIsolatedDatabase() {
            val properties = Properties().apply {
                OrderConcurrencyIntegrationTest::class.java.classLoader.getResourceAsStream("application.properties")!!.use { load(it) }
            }
            val original = properties.getProperty("spring.datasource.url")
            require(original.matches(Regex("jdbc:mysql://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/.*"))) {
                "This test only permits local MySQL; it never connects to a remote application database."
            }
            user = properties.getProperty("spring.datasource.username")
            password = properties.getProperty("spring.datasource.password")
            require(!user.contains("\${") && !password.contains("\${")) { "Use a local literal datasource configuration for this opt-in test." }
            val base = original.substringBefore('?').substringBeforeLast('/')
            serverUrl = "$base/?allowPublicKeyRetrieval=true&useSSL=false"
            testUrl = "$base/$schema?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
            require(schema.matches(Regex("agritech_concurrency_[a-f0-9]{32}")))
            DriverManager.getConnection(serverUrl, user, password).use { connection ->
                connection.createStatement().use { it.executeUpdate("CREATE DATABASE `$schema`") }
            }
            created = true
            context = AnnotationConfigApplicationContext(DatabaseConfig::class.java)
        }

        @JvmStatic @AfterAll
        fun removeOnlyTestDatabase() {
            try { if (::context.isInitialized) context.close() }
            finally {
                if (created) {
                    require(schema.matches(Regex("agritech_concurrency_[a-f0-9]{32}")))
                    DriverManager.getConnection(serverUrl, user, password).use { connection ->
                        connection.createStatement().use { it.executeUpdate("DROP DATABASE `$schema`") }
                    }
                }
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = [OrderRepository::class, ProductRepository::class, CartItemRepository::class, UserRepository::class])
    class DatabaseConfig {
        @Bean fun dataSource(): DataSource = DriverManagerDataSource(testUrl, user, password)
        @Bean fun entityManagerFactory(dataSource: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            setPackagesToScan("com.uet.agritech")
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-only", "hibernate.hbm2ddl.halt_on_error" to true))
        }
        @Bean fun transactionManager(factory: EntityManagerFactory) = JpaTransactionManager(factory)
        @Bean @org.springframework.context.annotation.Primary
        fun sharedEntityManager(factory: EntityManagerFactory): EntityManager = SharedEntityManagerCreator.createSharedEntityManager(factory)
        @Bean fun delivery(): DeliveryQuoteService = mockk()
        @Bean fun complaintService(complaints: OrderComplaintRepository, orders: OrderRepository,
            items: OrderItemRepository, em: EntityManager) = OrderComplaintService(complaints, orders, items,
                mockk<NotificationService>(relaxUnitFun = true), em)
        @Bean fun orderService(orders: OrderRepository, items: OrderItemRepository, carts: CartItemRepository,
            products: ProductRepository, users: UserRepository, em: EntityManager,
            addresses: ShippingAddressRepository, delivery: DeliveryQuoteService) = OrderService(
            orders, items, carts, products, users, mockk<RecommendationService>(relaxUnitFun = true),
            mockk<ReviewRepository>(), mockk<NotificationService>(relaxUnitFun = true), em, addresses, delivery)
    }

    private lateinit var service: OrderService
    private lateinit var orders: OrderRepository
    private lateinit var products: ProductRepository
    private lateinit var items: OrderItemRepository
    private lateinit var carts: CartItemRepository
    private lateinit var tx: TransactionTemplate
    private lateinit var productId: String
    private lateinit var first: CheckoutRequest
    private lateinit var second: CheckoutRequest

    @BeforeEach
    fun seedOnlyTestDatabase() {
        service = context.getBean(OrderService::class.java)
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service), "Checkout must use the real transaction interceptor")
        orders = context.getBean(OrderRepository::class.java)
        products = context.getBean(ProductRepository::class.java)
        items = context.getBean(OrderItemRepository::class.java)
        carts = context.getBean(CartItemRepository::class.java)
        val users = context.getBean(UserRepository::class.java)
        val addresses = context.getBean(ShippingAddressRepository::class.java)
        tx = TransactionTemplate(context.getBean(PlatformTransactionManager::class.java))
        tx.executeWithoutResult {
            context.getBean(OrderComplaintRepository::class.java).deleteAll()
            items.deleteAll(); carts.deleteAll(); orders.deleteAll(); products.deleteAll(); addresses.deleteAll(); users.deleteAll()
            context.getBean(EntityManager::class.java).flush()
            fun user(phone: String, role: Role) = users.save(User(fullName = phone, phoneNumber = phone,
                email = "$phone@example.test", password = "test-only", role = role))
            val seller = user("seller", Role.FARMER)
            val buyerA = user("buyer-a", Role.BUYER)
            val buyerB = user("buyer-b", Role.BUYER)
            val product = products.save(Product(name = "Last item", category = "Rau củ", price = 10000.0,
                quantity = 1.0, unit = "kg", description = "Concurrency test only", farmer = seller))
            productId = requireNotNull(product.id)
            fun request(buyer: User): CheckoutRequest {
                val address = addresses.save(ShippingAddress(user = buyer, receiverName = buyer.fullName,
                    phoneNumber = buyer.phoneNumber, addressDetail = "Test address", latitude = 21.0, longitude = 105.0))
                val cart = carts.save(CartItem(user = buyer, product = product, quantity = 1.0))
                return CheckoutRequest(selectedCartItemIds = listOf(requireNotNull(cart.id)), addressId = address.id,
                    expectedShippingFee = 0L, paymentMethod = "COD")
            }
            first = request(buyerA)
            second = request(buyerB)
            every { context.getBean(DeliveryQuoteService::class.java).quote(any(), any()) } answers {
                // Hold the winning transaction briefly so the other request reaches the same product lock.
                Thread.sleep(150)
                DeliveryQuoteResponse(1L, 30.0, listOf(SellerDeliveryQuote(requireNotNull(seller.id), seller.fullName,
                    emptyList(), DeliveryStatus.AVAILABLE, "Test quote", shippingFee = 0L)), true, 0L)
            }
        }
    }

    private fun race(vararg actions: () -> Unit): List<Result<Unit>> {
        val executor = Executors.newFixedThreadPool(actions.size)
        val ready = CountDownLatch(actions.size)
        val start = CountDownLatch(1)
        try {
            val futures = actions.map { action -> executor.submit(Callable {
                ready.countDown()
                check(start.await(10, TimeUnit.SECONDS))
                runCatching(action)
            }) }
            check(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            return futures.map { it.get(20, TimeUnit.SECONDS) }
        } finally { executor.shutdownNow() }
    }

    @RepeatedTest(5) fun `two buyers compete for one item - only one order commits`() {
        val results = race({ service.checkout(first, "buyer-a"); Unit }, { service.checkout(second, "buyer-b"); Unit })
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull()?.message.orEmpty().contains("Không đủ hàng"))
        assertEquals(1L, orders.count())
        assertEquals(1L, items.count())
        assertEquals(0.0, products.findById(productId).get().quantity)
        assertEquals(1L, carts.count(), "The unsuccessful buyer's cart is preserved")
    }

    @Test fun `repeated simultaneous checkout of the same cart cannot create two orders`() {
        val results = race({ service.checkout(first, "buyer-a"); Unit }, { service.checkout(first, "buyer-a"); Unit })
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1L, orders.count())
        assertEquals(0.0, products.findById(productId).get().quantity)
    }

    @Test fun `buyer and seller cancelling simultaneously restore inventory only once`() {
        val id = requireNotNull(service.checkout(first, "buyer-a").single().id)
        val results = race({ service.cancelOrderByBuyer(id, "buyer-a") },
            { service.updateStatus(id, OrderStatus.CANCELLED, "seller") })
        assertEquals(1, results.count { it.isSuccess })
        assertEquals("CANCELLED", orders.findById(id).get().status)
        assertEquals(1.0, products.findById(productId).get().quantity)
    }

    @Test fun `two different orders cancelling concurrently both restore their own quantity`() {
        tx.executeWithoutResult { products.findById(productId).get().quantity = 2.0 }
        val orderA = requireNotNull(service.checkout(first, "buyer-a").single().id)
        val orderB = requireNotNull(service.checkout(second, "buyer-b").single().id)
        val results = race({ service.cancelOrderByBuyer(orderA, "buyer-a") },
            { service.cancelOrderByBuyer(orderB, "buyer-b") })
        assertEquals(2, results.count { it.isSuccess })
        assertEquals(2.0, products.findById(productId).get().quantity)
    }

    @Test fun `checkout uses current cart quantity even when transaction snapshot predates a cart edit`() {
        tx.executeWithoutResult { products.findById(productId).get().quantity = 2.0 }
        tx.executeWithoutResult {
            context.getBean(UserRepository::class.java).findByPhoneNumber("buyer-a")
            val editor = Executors.newSingleThreadExecutor()
            try {
                editor.submit { tx.executeWithoutResult {
                    carts.findById(first.selectedCartItemIds.single()).get().quantity = 2.0
                } }.get(10, TimeUnit.SECONDS)
            } finally { editor.shutdownNow() }
            service.checkout(first, "buyer-a")
        }
        assertEquals(2.0, items.findAll().single().quantity)
        assertEquals(0.0, products.findById(productId).get().quantity)
    }

    @Test fun `two admins cannot overwrite a closed complaint through an older transaction snapshot`() {
        val orderId = requireNotNull(service.checkout(first, "buyer-a").single().id)
        tx.executeWithoutResult { orders.findById(orderId).get().apply {
            status = "SHIPPING"; sellerDeliveredAt = java.time.LocalDateTime.now()
        } }
        val complaintService = context.getBean(OrderComplaintService::class.java)
        val complaint = complaintService.create(orderId, "buyer-a", CreateOrderComplaintRequest(
            OrderComplaintReason.NOT_RECEIVED, "Test complaint, not real customer data."))
        val repo = context.getBean(OrderComplaintRepository::class.java)
        val snapshotsReady = java.util.concurrent.CyclicBarrier(2)
        fun close(admin: String) { tx.executeWithoutResult {
            repo.findById(complaint.id).get()
            snapshotsReady.await(10, TimeUnit.SECONDS)
            complaintService.update(complaint.id, admin, UpdateOrderComplaintRequest(
                OrderComplaintStatus.RESOLVED, "Test resolution from $admin"))
        } }
        val results = race({ close("admin-a") }, { close("admin-b") })
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(OrderComplaintStatus.RESOLVED, repo.findById(complaint.id).get().status)
        assertFalse(orders.findById(orderId).get().complaintOpen)
    }
}
