package com.uet.agritech.order

import com.uet.agritech.cart.CartItemRepository
import com.uet.agritech.order.dto.BuyerOrderItemDTO
import com.uet.agritech.order.dto.BuyerOrderResponse
import com.uet.agritech.order.dto.CheckoutRequest
import com.uet.agritech.order.dto.FarmerOrderItemDTO
import com.uet.agritech.order.dto.FarmerOrderResponse
import com.uet.agritech.order.dto.OrderStatus
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun checkout(request: CheckoutRequest, userPhone: String) {
        val user = userRepository.findByPhoneNumber(userPhone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        if (request.selectedCartItemIds.isEmpty()) {
            throw RuntimeException("Bạn chưa chọn món nào để thanh toán!")
        }

        val cartItems = cartItemRepository.findAllById(request.selectedCartItemIds)

        if (cartItems.isEmpty()) {
            throw RuntimeException("Dữ liệu giỏ hàng không hợp lệ!")
        }

        for (item in cartItems) {
            if (item.user.phoneNumber != userPhone) {
                throw RuntimeException("Giỏ hàng không hợp lệ!")
            }
        }

        for (item in cartItems) {
            if (item.quantity > item.product.quantity) {
                throw RuntimeException("Không đủ hàng cho sản phẩm")
            }
        }

        val totalAmount = cartItems.sumOf { it.product.price * it.quantity }

        val newOrder = Order(
            user = user,
            totalAmount = totalAmount,
            shippingAddress = request.shippingAddress,
            phoneNumber = request.phoneNumber
        )
        val savedOrder = orderRepository.save(newOrder)

        val orderItems = cartItems.map { cartItem ->
            val product = cartItem.product
            product.quantity -= cartItem.quantity
            productRepository.save(product)

            OrderItem(
                order = savedOrder,
                product = product,
                quantity = cartItem.quantity,
                price = product.price
            )
        }
        orderItemRepository.saveAll(orderItems)

        cartItemRepository.deleteAll(cartItems)
    }

    fun getOrdersForFarmer(farmerPhone: String): List<FarmerOrderResponse> {
        val farmer = userRepository.findByPhoneNumber(farmerPhone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        val mySoldItems = orderItemRepository.findAllByProductFarmer(farmer)

        val groupedByOrder = mySoldItems.groupBy { it.order }

        return groupedByOrder.map { (order, items) ->
            FarmerOrderResponse(
                orderId = order.id!!,
                orderDate = order.orderDate.toString(),
                buyerPhone = order.phoneNumber,
                buyerName = order.user.fullName,
                buyerId = order.user.id!!,
                shippingAddress = order.shippingAddress,
                status = order.status,
                items = items.map { item ->
                    FarmerOrderItemDTO(
                        productName = item.product.name,
                        quantity = item.quantity,
                        unit = item.product.unit,
                        price = item.price,
                        thumbnail = item.product.imageUrls.firstOrNull() ?: ""
                    )
                },
                totalRevenueFromThisOrder = items.sumOf { it.price * it.quantity }
            )
        }.sortedByDescending { it.orderDate }
    }

    @Transactional
    fun updateStatus(orderId: Long, newStatus: OrderStatus, sellerPhone: String) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { RuntimeException("Không tìm thấy đơn hàng!") }

        val orderItems = orderItemRepository.findAllByOrder(order)

        val isMyOrder = orderItems.any {
            it.product.farmer.phoneNumber == sellerPhone
        }

        if (!isMyOrder) {
            throw RuntimeException("Bạn không có quyền xử lý đơn hàng này!")
        }

        val currentStatus = try {
            OrderStatus.valueOf(order.status)
        } catch (e: Exception) {
            OrderStatus.PENDING
        }

        if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.CANCELLED) {
            throw RuntimeException("Đơn hàng đã đóng ở trạng thái $currentStatus, không thể sửa đổi!")
        }

        val isValidTransition = when (currentStatus) {
            OrderStatus.PENDING -> newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.CANCELLED
            OrderStatus.CONFIRMED -> newStatus == OrderStatus.SHIPPING || newStatus == OrderStatus.CANCELLED
            OrderStatus.SHIPPING -> newStatus == OrderStatus.COMPLETED || newStatus == OrderStatus.CANCELLED
            else -> false
        }

        if (!isValidTransition) {
            throw RuntimeException("Quy trình sai")
        }

        if (newStatus == OrderStatus.CANCELLED) {
            for (item in orderItems) {
                val product = item.product
                product.quantity += item.quantity
                productRepository.save(product)
            }
        }

        order.status = newStatus.name
        orderRepository.save(order)
    }

    fun getMyOrders(buyerPhone: String, status: String? = null): List<BuyerOrderResponse> {
        val user = userRepository.findByPhoneNumber(buyerPhone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        val myOrders = if (status.isNullOrBlank()) {
            orderRepository.findAllByUserOrderByOrderDateDesc(user)
        } else {
            orderRepository.findAllByUserAndStatusOrderByOrderDateDesc(user, status.uppercase())
        }

        return myOrders.map { order ->
            val orderItems = orderItemRepository.findAllByOrder(order)

            BuyerOrderResponse(
                orderId = order.id!!,
                orderDate = order.orderDate.toString(),
                status = order.status,
                shippingAddress = order.shippingAddress,
                totalAmount = order.totalAmount,
                items = orderItems.map { item ->
                    BuyerOrderItemDTO(
                        productId = item.product.id!!,
                        productName = item.product.name,
                        farmerName = item.product.farmer.fullName,
                        quantity = item.quantity,
                        unit = item.product.unit,
                        price = item.price,
                        thumbnail = item.product.imageUrls.firstOrNull() ?: ""
                    )
                }
            )
        }
    }

    fun countMyOrdersByStatus(buyerPhone: String): Map<String, Long> {
        val user = userRepository.findByPhoneNumber(buyerPhone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        val counts = orderRepository.countOrdersByStatusForUser(user)

        return counts.associate { it.getStatus() to it.getCount() }
    }
}