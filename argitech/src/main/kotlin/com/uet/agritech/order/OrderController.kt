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
class OrderController(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val vnpayService: VnpayService
) {

    @PostMapping("/checkout")
    fun checkout(@RequestBody request: CheckoutRequest): ResponseEntity<OrderMessageResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name
        val createdOrder = orderService.checkout(request, phone.toString())

        return ResponseEntity.ok(
            OrderMessageResponse(
                message = "Đặt hàng thành công!",
                orderId = createdOrder.id
            )
        )
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
    fun getMyOrders(
        @RequestParam(required = false) status: String?
    ): ResponseEntity<List<BuyerOrderResponse>> {

        val phone = SecurityContextHolder.getContext().authentication?.name

        val orders = orderService.getMyOrders(phone.toString(), status)
        return ResponseEntity.ok(orders)
    }

    @GetMapping("/my-orders/count")
    fun countMyOrdersByStatus(): ResponseEntity<Map<String, Long>> {
        val phone = SecurityContextHolder.getContext().authentication?.name
            ?: throw RuntimeException("Chưa đăng nhập")

        val statusCounts = orderService.countMyOrdersByStatus(phone)
        return ResponseEntity.ok(statusCounts)
    }

    @PostMapping("/{id}/vnpay-payment")
    fun getVnpayPaymentUrl(
        @PathVariable id: Long,
        request: jakarta.servlet.http.HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        val order = orderRepository.findById(id)
            .orElseThrow { RuntimeException("Không tìm thấy đơn hàng #$id") }

        val ipAddress = request.remoteAddr ?: "127.0.0.1"

        val paymentUrl = vnpayService.createPaymentUrl(
            orderId = order.id,
            amount = order.totalAmount.toLong(),
            ipAddress = ipAddress
        )

        println("LINK VNPAY: $paymentUrl")
        return ResponseEntity.ok(mapOf("paymentUrl" to paymentUrl))
    }

    @PutMapping("/{id}/mark-as-paid")
    fun markOrderAsPaid(@PathVariable id: Long): ResponseEntity<Map<String, String>> {
        val order = orderRepository.findById(id)
            .orElseThrow { RuntimeException("Không tìm thấy đơn hàng #$id") }

        order.status = "PAID"
        orderRepository.save(order)

        return ResponseEntity.ok(mapOf("message" to "Cập nhật trạng thái thành công"))
    }

    @PutMapping("/{orderId}/cancel")
    fun cancelOrderByBuyer(@PathVariable orderId: Long): ResponseEntity<OrderMessageResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name
            ?: throw RuntimeException("Chưa đăng nhập")
        orderService.cancelOrderByBuyer(orderId, phone)
        return ResponseEntity.ok(OrderMessageResponse("Hủy đơn hàng thành công!"))
    }
}