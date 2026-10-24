package com.uet.agritech.product

import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import io.mockk.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.*

class ProductStockServiceTest {
    private val products = mockk<ProductRepository>()
    private val users = mockk<UserRepository>()
    private val service = ProductStockService(products, users)

    @Test
    fun `invalid stock is rejected before changing inventory`() {
        for (quantity in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val error = assertFailsWith<ResponseStatusException> {
                service.updateStock("seller", "p1", UpdateProductStockRequest(quantity))
            }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
        verify { products wasNot Called }
    }

    @Test
    fun `only the owner can change inventory`() {
        val seller = mockk<User>()
        val owner = mockk<User>()
        val product = mockk<Product>()
        every { seller.id } returns "other"
        every { owner.id } returns "owner"
        every { product.farmer } returns owner
        every { users.findByPhoneNumber("seller") } returns Optional.of(seller)
        every { products.findForStockUpdate("p1") } returns Optional.of(product)
        val error = assertFailsWith<ResponseStatusException> {
            service.updateStock("seller", "p1", UpdateProductStockRequest(10.0))
        }
        assertEquals(HttpStatus.FORBIDDEN, error.statusCode)
        verify(exactly = 0) { products.save(any()) }
    }

    @Test
    fun `zero stock and restocking preserve product and visibility`() {
        val seller = mockk<User>()
        val product = mockk<Product>()
        var stock = 5.0
        every { seller.id } returns "owner"
        every { product.id } returns "p1"
        every { product.farmer } returns seller
        every { product.quantity } answers { stock }
        every { product.quantity = any() } answers { stock = firstArg() }
        every { users.findByPhoneNumber("seller") } returns Optional.of(seller)
        every { products.findForStockUpdate("p1") } returns Optional.of(product)
        every { products.save(product) } returns product
        assertEquals(0.0, service.updateStock("seller", "p1", UpdateProductStockRequest(0.0)).quantity)
        assertEquals(2.5, service.updateStock("seller", "p1", UpdateProductStockRequest(2.5)).quantity)
        verify(exactly = 2) { products.findForStockUpdate("p1") }
        verify(exactly = 0) { product.hidden = any() }
        verify(exactly = 0) { products.delete(any()) }
    }
}
