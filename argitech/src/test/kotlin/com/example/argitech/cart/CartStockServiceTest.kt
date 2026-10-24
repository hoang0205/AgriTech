package com.uet.agritech.cart

import com.uet.agritech.cart.dto.AddToCartRequest
import com.uet.agritech.product.Product
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.*
import io.mockk.*
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.*

class CartStockServiceTest {
    private val carts = mockk<CartItemRepository>()
    private val products = mockk<ProductRepository>()
    private val users = mockk<UserRepository>()
    private val service = CartService(carts, products, users, mockk<RecommendationService>())
    private val buyer = User(id = "buyer", fullName = "Buyer", phoneNumber = "buyer", email = "buyer@test", password = "test", role = Role.BUYER)
    private val seller = User(id = "seller", fullName = "Seller", phoneNumber = "seller", email = "seller@test", password = "test", role = Role.FARMER)
    private val product = Product(id = "p1", name = "Rau", category = "Rau", price = 10000.0, quantity = 0.0, unit = "kg", description = "", farmer = seller)

    @Test
    fun `sold out items stay in cart and become available after restock`() {
        val item = CartItem(id = 1L, user = buyer, product = product, quantity = 2.0)
        every { users.findByPhoneNumber("buyer") } returns Optional.of(buyer)
        every { carts.findAllByUser(buyer) } returns listOf(item)
        val soldOut = service.getMyCart("buyer").single()
        assertFalse(soldOut.available)
        assertEquals(0.0, soldOut.availableQuantity)
        assertEquals(2.0, soldOut.quantity)
        product.quantity = 4.0
        val restocked = service.getMyCart("buyer").single()
        assertTrue(restocked.available)
        assertEquals(4.0, restocked.availableQuantity)
        product.hidden = true
        assertFalse(service.getMyCart("buyer").single().available)
        verify(exactly = 0) { carts.delete(any()) }
    }

    @Test
    fun `cannot add sold out product to cart`() {
        every { users.findByPhoneNumber("buyer") } returns Optional.of(buyer)
        every { products.findById("p1") } returns Optional.of(product)
        every { carts.findByUserAndProduct(buyer, product) } returns null
        assertFailsWith<IllegalArgumentException> {
            service.addToCart(AddToCartRequest("p1", 1.0), "buyer")
        }
        verify(exactly = 0) { carts.save(any()) }
    }
}
