package com.uet.agritech.cart

import com.uet.agritech.cart.dto.AddToCartRequest
import com.uet.agritech.cart.dto.CartResponse
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.RecommendationService
import com.uet.agritech.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CartService(
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val interactionService: RecommendationService
) {
    @Transactional
    fun addToCart(request: AddToCartRequest, phoneNumber: String) {
        val productId = request.productId.trim()

        require(productId.isNotBlank()) {
            "ID sản phẩm không được để trống"
        }

        require(request.quantity.isFinite() && request.quantity > 0.0) {
            "Số lượng thêm vào giỏ phải là số hợp lệ và lớn hơn 0"
        }

        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không tìm thấy người dùng"
                )
            }

        val product = productRepository.findById(productId)
            .orElseThrow {
                RuntimeException("Sản phẩm không tồn tại")
            }

        if (product.farmer.id == user.id) {
            throw RuntimeException(
                "Bạn không thể thêm sản phẩm của chính mình vào giỏ hàng!"
            )
        }

        require(product.quantity.isFinite() && product.quantity >= 0.0) {
            "Dữ liệu tồn kho sản phẩm không hợp lệ"
        }

        val existingItem = cartItemRepository.findByUserAndProduct(
            user,
            product
        )

        val currentQuantity = existingItem?.quantity ?: 0.0

        require(currentQuantity.isFinite() && currentQuantity >= 0.0) {
            "Số lượng hiện tại trong giỏ không hợp lệ"
        }

        val newQuantity = currentQuantity + request.quantity

        require(newQuantity.isFinite() && newQuantity > 0.0) {
            "Tổng số lượng trong giỏ không hợp lệ"
        }

        require(newQuantity <= product.quantity) {
            "Tổng số lượng trong giỏ vượt quá hàng trong kho!"
        }

        if (existingItem != null) {
            existingItem.quantity = newQuantity
            cartItemRepository.save(existingItem)
        } else {
            cartItemRepository.save(
                CartItem(
                    user = user,
                    product = product,
                    quantity = newQuantity
                )
            )
        }

        interactionService.recordInteraction(
            phoneNumber,
            product,
            "ADD_TO_CART",
            2.0
        )
    }

    @Transactional
    fun updateQuantity(
        cartItemId: Long,
        quantity: Double,
        phoneNumber: String
    ) {
        require(quantity.isFinite() && quantity >= 0.0) {
            "Số lượng phải là số hợp lệ và không được âm"
        }

        val cartItem = cartItemRepository.findById(cartItemId)
            .orElseThrow {
                RuntimeException("Không tìm thấy món đồ này trong giỏ!")
            }

        if (cartItem.user.phoneNumber != phoneNumber) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Bạn không có quyền sửa giỏ hàng người khác!"
            )
        }

        if (quantity == 0.0) {
            cartItemRepository.delete(cartItem)
            return
        }

        val stock = cartItem.product.quantity

        require(stock.isFinite() && stock >= 0.0) {
            "Dữ liệu tồn kho sản phẩm không hợp lệ"
        }

        require(quantity <= stock) {
            "Kho không đủ hàng!"
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