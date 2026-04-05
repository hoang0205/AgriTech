package com.uet.agritech.cart

import com.uet.agritech.cart.dto.AddToCartRequest
import com.uet.agritech.cart.dto.CartMessageResponse
import com.uet.agritech.cart.dto.CartResponse
import com.uet.agritech.cart.dto.UpdateCartRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/cart")
class CartController(private val cartService: CartService) {

    @PostMapping("/add")
    fun addToCart(@RequestBody request: AddToCartRequest): ResponseEntity<CartMessageResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        cartService.addToCart(request, phone.toString())
        return ResponseEntity.ok(CartMessageResponse(message = "Đã thêm vào giỏ hàng!"))
    }

    @GetMapping
    fun getCart(): ResponseEntity<List<CartResponse>> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        return ResponseEntity.ok(cartService.getMyCart(phone.toString()))
    }

    @PutMapping("/{cartItemId}")
    fun updateCartItem(
        @PathVariable cartItemId: Long,
        @RequestBody request: UpdateCartRequest
    ): ResponseEntity<CartMessageResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        cartService.updateQuantity(cartItemId, request.quantity, phone.toString())
        return ResponseEntity.ok(CartMessageResponse("Đã cập nhật số lượng!"))
    }

    @DeleteMapping("/{cartItemId}")
    fun removeCartItem(@PathVariable cartItemId: Long): ResponseEntity<CartMessageResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        cartService.removeCartItem(cartItemId, phone.toString())
        return ResponseEntity.ok(CartMessageResponse("Đã xóa món đồ khỏi giỏ hàng!"))
    }
}