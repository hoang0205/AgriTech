package com.uet.agritech.cart

import com.uet.agritech.cart.dto.AddToCartRequest
import com.uet.agritech.cart.dto.CartResponse
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CartService(
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun addToCart(request: AddToCartRequest, phoneNumber: String) {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { RuntimeException("Không tìm thấy người dùng") }
        val product = productRepository.findById(request.productId)
            .orElseThrow { RuntimeException("Sản phẩm không tồn tại") }

        if (request.quantity > product.quantity) {
            throw RuntimeException("Số lượng mua vượt quá hàng trong kho!")
        }

        val existingItem = cartItemRepository.findByUserAndProduct(user, product)

        if (existingItem != null) {
            existingItem.quantity += request.quantity
            cartItemRepository.save(existingItem)
        } else {
            val newItem = CartItem(user = user, product = product, quantity = request.quantity)
            cartItemRepository.save(newItem)
        }
    }

    @Transactional
    fun updateQuantity(cartItemId: Long, quantity: Double, phoneNumber: String) {
        val cartItem = cartItemRepository.findById(cartItemId)
            .orElseThrow { RuntimeException("Không tìm thấy món đồ này trong giỏ!") }

        if (cartItem.user.phoneNumber != phoneNumber) {
            throw RuntimeException("Bạn không có quyền sửa giỏ hàng người khác!")
        }

        if (quantity <= 0) {
            cartItemRepository.delete(cartItem)
            return
        }

        if (quantity > cartItem.product.quantity) {
            throw RuntimeException("Kho không đủ hàng!")
        }

        cartItem.quantity = quantity
        cartItemRepository.save(cartItem)
    }

    @Transactional
    fun removeCartItem(cartItemId: Long, phoneNumber: String) {
        val cartItem = cartItemRepository.findById(cartItemId)
            .orElseThrow { RuntimeException("Không tìm thấy món đồ này trong giỏ!") }

        if (cartItem.user.phoneNumber != phoneNumber) {
            throw RuntimeException("Bạn không có quyền xóa giỏ hàng người khác!")
        }

        cartItemRepository.delete(cartItem)
    }

    fun getMyCart(phoneNumber: String): List<CartResponse> {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { RuntimeException("Không tìm thấy người dùng") }

        return cartItemRepository.findAllByUser(user).map { item ->
            CartResponse(
                cartItemId = item.id!!,
                productId = item.product.id!!,
                productName = item.product.name,
                price = item.product.price,
                quantity = item.quantity,
                thumbnail = item.product.imageUrls.firstOrNull() ?: "",
                unit = item.product.unit,
                totalPrice = item.product.price * item.quantity,
                farmerId = item.product.farmer.id!!,
                farmerName = item.product.farmer.fullName,
                farmerPhone = item.product.farmer.phoneNumber
            )
        }
    }
}