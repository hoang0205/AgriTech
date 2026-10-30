package com.uet.agritech.order

import com.uet.agritech.order.dto.BuyerOrderResponse
import com.uet.agritech.order.dto.CheckoutRequest
import com.uet.agritech.order.dto.FarmerOrderResponse
import com.uet.agritech.order.dto.OrderMessageResponse
import com.uet.agritech.order.dto.RevenueSummaryDto
import com.uet.agritech.order.dto.UpdateOrderStatusRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val vnpayService: VnpayService,
    private val orderDetailService: OrderDetailService
) {

    @GetMapping("/{orderId}")
    fun getDetail(@PathVariable orderId: Long, principal: java.security.Principal): OrderDetailResponse =
        orderDetailService.get(orderId, principal.name)

    @PostMapping("/{orderId}/confirm-received")
    fun confirmReceived(@PathVariable orderId: Long, principal: java.security.Principal): ResponseEntity<OrderMessageResponse> {
        orderService.confirmReceived(orderId, principal.name)
        return ResponseEntity.ok(OrderMessageResponse("Đã xác nhận nhận hàng thành công!"))
    }

    @PostMapping("/checkout")
    fun checkout(
        @RequestBody request: CheckoutRequest
    ): ResponseEntity<OrderMessageResponse> {
        val phone = SecurityContextHolder.getContext()
            .authentication?.name
            ?: throw RuntimeException("Chưa đăng nhập")

        val createdOrders = orderService.checkout(request, phone)
        val orderIds = createdOrders.map { it.id!! }

        val message = if (orderIds.size == 1) {
            "Đặt hàng thành công!"
        } else {
            "Đặt hàng thành công! Đã tạo ${orderIds.size} đơn theo người bán."
        }

        return ResponseEntity.ok(
            OrderMessageResponse(
                message = message,
                orderId = orderIds.singleOrNull(),
                orderIds = orderIds
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
        val phone = (SecurityContextHolder.getContext().authentication?.name ?: throw IllegalStateException("Chưa đăng nhập"))
        val paymentUrl = vnpayService.createForOrder(id, phone, request.remoteAddr ?: "127.0.0.1")
        return ResponseEntity.ok(mapOf("paymentUrl" to paymentUrl))
    }

    @GetMapping("/vnpay/ipn")
    fun vnpayIpn(@RequestParam params: Map<String, String>): Map<String, String> = vnpayService.processResult(params)

    @PostMapping("/{id}/vnpay-result")
    fun verifyVnpay(@PathVariable id: Long, @RequestBody params: Map<String, String>): ResponseEntity<Map<String, String>> {
        val result = vnpayService.processResult(params, id, (SecurityContextHolder.getContext().authentication?.name ?: throw IllegalStateException("Chưa đăng nhập")))
        if (result["RspCode"] != "00") return ResponseEntity.badRequest().body(mapOf("message" to result.getValue("Message")))
        return if (params["vnp_ResponseCode"] == "00" && params["vnp_TransactionStatus"] == "00") ResponseEntity.ok(mapOf("message" to "Payment verified"))
        else ResponseEntity.badRequest().body(mapOf("message" to "Payment failed"))
    }

    @PutMapping("/{id}/mark-as-paid")
    fun markOrderAsPaid(
        @PathVariable id: Long
    ): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(403).body(
            mapOf(
                "message" to "Chức năng xác nhận thanh toán online đang tạm khóa."
            )
        )
    }

    @PutMapping("/{orderId}/cancel")
    fun cancelOrderByBuyer(@PathVariable orderId: Long): ResponseEntity<OrderMessageResponse> {
        val phone = SecurityContextHolder.getContext().authentication?.name
            ?: throw RuntimeException("Chưa đăng nhập")
        orderService.cancelOrderByBuyer(orderId, phone)
        return ResponseEntity.ok(OrderMessageResponse("Hủy đơn hàng thành công!"))
    }

    @RestController
    @RequestMapping("/api/statistics")
    class StatisticController(
        private val orderService: OrderService
    ) {
        @GetMapping("/revenue")
        fun getRevenueSummary(): ResponseEntity<RevenueSummaryDto> {
            val farmerPhone = SecurityContextHolder.getContext().authentication?.name
                ?: throw RuntimeException("Người dùng chưa đăng nhập!")

            val summary = orderService.getFarmerRevenue(farmerPhone)
            return ResponseEntity.ok(summary)
        }
    }
}
