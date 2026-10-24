package com.uet.agritech.order

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.time.LocalDateTime

data class DeliveryManagementView(
    val method: DeliveryMethod?, val actualCost: Long?, val customerShippingFee: Long,
    val ghnEstimatedFee: Long?, val ghnActive: Boolean, val status: String,
    val costUpdatedAt: String?
)
data class ChooseDeliveryRequest(val method: DeliveryMethod)
data class ShippingCostRequest(val actualCost: Long?)

@Service
class DeliveryManagementService(private val orders: OrderRepository, private val items: OrderItemRepository) {
    @Transactional
    fun view(id: Long, phone: String): DeliveryManagementView = viewOf(owned(id, phone))

    @Transactional
    fun choose(id: Long, phone: String, method: DeliveryMethod): DeliveryManagementView {
        val order = owned(id, phone)
        require(order.status == "CONFIRMED") { "Chỉ chọn hoặc đổi cách giao trước khi bàn giao hàng." }
        require(!ghnActive(order)) { "Cần hủy hoặc đối chiếu yêu cầu GHN trước khi đổi cách giao." }
        require(method != DeliveryMethod.GHN || order.ghnRequestJson == null) {
            "Vận đơn GHN của đơn này đã hủy. Hãy chọn tự tổ chức giao; bản hiện tại chưa tạo vận đơn GHN lần hai cho cùng đơn."
        }
        order.deliveryMethod = method
        // This is the total real delivery expense for the purchase, including any
        // already incurred pickup/return costs. Changing method must not erase it.
        orders.save(order)
        return viewOf(order)
    }

    @Transactional
    fun cost(id: Long, phone: String, actualCost: Long?): DeliveryManagementView {
        val order = owned(id, phone)
        require(order.status in setOf("CONFIRMED", "SHIPPING", "COMPLETED", "CANCELLED")) {
            "Chỉ ghi chi phí cho đơn đã xác nhận, đang giao hoặc đã đóng."
        }
        require(actualCost == null || actualCost in 0..100_000_000L) {
            "Chi phí giao phải từ 0 đến 100 triệu đồng hoặc để trống khi chưa biết."
        }
        order.actualShippingCost = actualCost
        order.shippingCostUpdatedAt = LocalDateTime.now()
        orders.save(order)
        return viewOf(order)
    }

    private fun owned(id: Long, phone: String): Order {
        val order = orders.findForStatusUpdate(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.")
        }
        val sellers = items.findAllByOrder(order).map { it.product.farmer.phoneNumber }.toSet()
        if (sellers != setOf(phone)) throw ResponseStatusException(HttpStatus.FORBIDDEN,
            "Chỉ người bán của đơn được chọn cách giao và ghi chi phí.")
        return order
    }
    private fun ghnActive(order: Order) = order.hasActiveGhnShipment()
    private fun viewOf(order: Order) = DeliveryManagementView(
        order.deliveryMethod, order.actualShippingCost, order.shippingFee, order.ghnShippingFee,
        ghnActive(order), order.status, order.shippingCostUpdatedAt?.toString())
}

@RestController
@RequestMapping("/api/orders/{id}/delivery")
class DeliveryManagementController(private val service: DeliveryManagementService) {
    @GetMapping
    fun view(@PathVariable id: Long, principal: Principal) = service.view(id, principal.name)
    @PutMapping("/method")
    fun choose(@PathVariable id: Long, principal: Principal, @RequestBody request: ChooseDeliveryRequest) =
        service.choose(id, principal.name, request.method)
    @PutMapping("/cost")
    fun cost(@PathVariable id: Long, principal: Principal, @RequestBody request: ShippingCostRequest) =
        service.cost(id, principal.name, request.actualCost)
}
