package com.uet.agritech.product

import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MyProductEditServiceTest {
    private val products = mockk<ProductRepository>()
    private val users = mockk<UserRepository>()
    private val service = MyProductEditService(products, users)
    private val seller = mockk<User>()
    private val product = Product(
        id = "p1", name = "Rau cũ", category = "Rau củ", price = 20000.0,
        quantity = 5.0, unit = "kg", description = "Mô tả cũ",
        imageUrls = mutableListOf("https://example.com/old.jpg"), farmer = seller, costPrice = 12000.0
    )
    private val request = UpdateMyProductRequest(
        name = "Rau mới", category = "Rau củ", price = 25000.0, unit = "bó",
        description = "Mô tả mới", imageUrls = listOf("https://example.com/new.jpg"), costPrice = 15000.0
    )

    init {
        every { seller.id } returns "owner"
        every { users.findByPhoneNumber("seller") } returns Optional.of(seller)
        every { products.findById("p1") } returns Optional.of(product)
        every { products.findForStockUpdate("p1") } returns Optional.of(product)
        every { products.save(product) } returns product
    }

    @Test
    fun `other users cannot read private cost or edit product`() {
        val other = mockk<User>()
        every { other.id } returns "other"
        every { users.findByPhoneNumber("other") } returns Optional.of(other)
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            service.getProduct("other", "p1")
        }.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            service.updateProduct("other", "p1", request)
        }.statusCode)
        assertEquals("Rau cũ", product.name)
        verify(exactly = 0) { products.save(any()) }
    }

    @Test
    fun `editing information preserves inventory sold while form was open`() {
        product.quantity = 3.0
        val updated = service.updateProduct("seller", "p1", request)
        assertEquals(3.0, updated.quantity)
        assertEquals("Rau mới", updated.name)
        assertEquals(25000.0, updated.price)
        assertEquals(15000.0, updated.costPrice)
        assertEquals("bó", updated.unit)
        assertEquals(request.imageUrls, updated.imageUrls)
    }

    @Test
    fun `stale inventory rejects the entire edit`() {
        product.quantity = 3.0
        val error = assertFailsWith<ResponseStatusException> {
            service.updateProduct("seller", "p1", request.copy(quantity = 10.0, expectedQuantity = 5.0))
        }
        assertEquals(HttpStatus.CONFLICT, error.statusCode)
        assertEquals(3.0, product.quantity)
        assertEquals("Rau cũ", product.name)
        assertEquals(20000.0, product.price)
        verify(exactly = 0) { products.save(any()) }
    }

    @Test
    fun `seller can mark sold out and clear an unknown cost`() {
        val updated = service.updateProduct("seller", "p1",
            request.copy(quantity = 0.0, expectedQuantity = 5.0, costPrice = null))
        assertEquals(0.0, updated.quantity)
        assertNull(updated.costPrice)
        assertEquals(false, product.hidden)
    }

    @Test
    fun `invalid prices and images are rejected without changing product`() {
        val invalid = listOf(
            request.copy(price = Double.NaN), request.copy(price = 0.0),
            request.copy(costPrice = -1.0), request.copy(costPrice = Double.POSITIVE_INFINITY),
            request.copy(imageUrls = emptyList()), request.copy(quantity = -1.0, expectedQuantity = 5.0)
        )
        invalid.forEach { badRequest ->
            assertFailsWith<IllegalArgumentException> { service.updateProduct("seller", "p1", badRequest) }
        }
        assertEquals(20000.0, product.price)
        assertEquals(5.0, product.quantity)
        verify(exactly = 0) { products.save(any()) }
    }
}
