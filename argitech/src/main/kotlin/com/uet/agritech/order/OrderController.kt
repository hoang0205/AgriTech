package com.uet.agritech.order

import com.uet.agritech.order.dto.BuyerOrderResponse
import com.uet.agritech.order.dto.CheckoutRequest
import com.uet.agritech.order.dto.FarmerOrderResponse
import com.uet.agritech.order.dto.OrderMessageResponse
import com.uet.agritech.order.dto.UpdateOrderStatusRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {

    @PostMapping("/checkout")
    fun checkout(@RequestBody request: CheckoutRequest): ResponseEntity<OrderMessageResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        orderService.checkout(request, phone.toString())

        return ResponseEntity.ok(OrderMessageResponse("Đặt hàng thành công!"))
    }

    @GetMapping("/seller")
    fun getSellerOrders(): ResponseEntity<List<FarmerOrderResponse>> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        val orders = orderService.getOrdersForFarmer(phone.toString())
        return ResponseEntity.ok(orders)
    }


    @PatchMapping("/{orderId}/status")
    fun updateStatus(
        @PathVariable orderId: Long,
        @RequestBody request: UpdateOrderStatusRequest
    ): ResponseEntity<OrderMessageResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        orderService.updateStatus(orderId, request.status, phone.toString())

        return ResponseEntity.ok(OrderMessageResponse("Cập nhật trạng thái đơn hàng thành công!"))
    }

    @GetMapping("/my-orders")
    fun getMyOrders(): ResponseEntity<List<BuyerOrderResponse>> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        val orders = orderService.getMyOrders(phone.toString())
        return ResponseEntity.ok(orders)
    }
}