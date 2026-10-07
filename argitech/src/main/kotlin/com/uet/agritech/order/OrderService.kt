package com.uet.agritech.order

import com.uet.agritech.cart.CartItemRepository
import com.uet.agritech.notification.NotificationService
import com.uet.agritech.order.dto.BuyerOrderItemDTO
import com.uet.agritech.order.dto.BuyerOrderResponse
import com.uet.agritech.order.dto.CheckoutRequest
import com.uet.agritech.order.dto.FarmerOrderItemDTO
import com.uet.agritech.order.dto.FarmerOrderResponse
import com.uet.agritech.order.dto.OrderStatus
import com.uet.agritech.order.dto.RevenueSummaryDto
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.review.ReviewRepository
import com.uet.agritech.user.RecommendationService
import com.uet.agritech.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val interactionService: RecommendationService,
    private val reviewRepository: ReviewRepository,
    private val notificationService: NotificationService
) {

    @Transactional
    fun checkout(
        request: CheckoutRequest,
        userPhone: String
    ): List<Order> {
        val user = userRepository.findByPhoneNumber(userPhone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        if (request.selectedCartItemIds.isEmpty()) {
            throw RuntimeException("Bạn chưa chọn món nào để thanh toán!")
        }

        val selectedIds = request.selectedCartItemIds.distinct()
        val cartItems = cartItemRepository.findAllById(selectedIds)

        if (cartItems.isEmpty() || cartItems.size != selectedIds.size) {
            throw RuntimeException(
                "Giỏ hàng đã thay đổi. Vui lòng tải lại trước khi đặt hàng!"
            )
        }

        for (item in cartItems) {
            if (item.user.phoneNumber != userPhone) {
                throw RuntimeException("Giỏ hàng không hợp lệ!")
            }

            if (!item.quantity.isFinite() || item.quantity <= 0.0) {
                throw RuntimeException("Số lượng sản phẩm không hợp lệ!")
            }

            if (item.quantity > item.product.quantity) {
                throw RuntimeException(
                    "Không đủ hàng cho sản phẩm: ${item.product.name}"
                )
            }
        }

        val itemsBySeller = cartItems.groupBy {
            it.product.farmer.phoneNumber
        }

        val isVnpay = request.paymentMethod.equals(
            "VNPAY",
            ignoreCase = true
        )

        if (isVnpay && itemsBySeller.size > 1) {
            throw RuntimeException(
                "Đơn nhiều người bán hiện chưa hỗ trợ VNPay. Vui lòng chọn COD."
            )
        }

        val initialStatus = if (isVnpay) {
            "UNPAID"
        } else {
            OrderStatus.PENDING.name
        }

        val createdOrders = mutableListOf<Order>()
        val buyerDisplayName = user.fullName.ifBlank {
            user.phoneNumber
        }

        for (sellerCartItems in itemsBySeller.values) {
            val seller = sellerCartItems.first().product.farmer

            val sellerTotal = sellerCartItems.sumOf {
                it.product.price * it.quantity
            }

            val newOrder = Order(
                user = user,
                totalAmount = sellerTotal,
                shippingAddress = request.shippingAddress,
                phoneNumber = request.phoneNumber
            )
            newOrder.status = initialStatus

            val savedOrder = orderRepository.save(newOrder)

            val orderItems = sellerCartItems.map { cartItem ->
                val product = cartItem.product

                product.quantity -= cartItem.quantity
                productRepository.save(product)

                interactionService.recordInteraction(
                    userPhone,
                    product,
                    "PURCHASE",
                    5.0
                )

                OrderItem(
                    order = savedOrder,
                    product = product,
                    quantity = cartItem.quantity,
                    price = product.price
                )
            }

            orderItemRepository.saveAll(orderItems)
            createdOrders.add(savedOrder)

            if (initialStatus == OrderStatus.PENDING.name) {
                notificationService.sendOrderStatusNotification(
                    recipient = user,
                    orderId = savedOrder.id!!,
                    status = OrderStatus.PENDING.name,
                    title = "Đặt hàng thành công! (Mã #${savedOrder.id})",
                    body = "Đơn hàng của bạn đang chờ người bán xác nhận."
                )
            }

            val itemsSummary = orderItems.joinToString(", ") {
                "${it.product.name} (x${it.quantity})"
            }

            notificationService.sendOrderStatusNotification(
                recipient = seller,
                orderId = savedOrder.id!!,
                status = "NEW_ORDER_SELLER",
                title = "Bạn có đơn hàng mới! (Mã #${savedOrder.id})",
                body = "Khách hàng $buyerDisplayName vừa đặt mua: " +
                        "$itemsSummary. Vui lòng xác nhận đơn hàng!"
            )
        }

        cartItemRepository.deleteAll(cartItems)

        return createdOrders
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

        if (orderItems.isEmpty()) {
            throw RuntimeException("Đơn hàng không có sản phẩm!")
        }

        val sellerPhones = orderItems
            .map { it.product.farmer.phoneNumber }
            .toSet()

        if (sellerPhone !in sellerPhones) {
            throw RuntimeException("Bạn không có quyền xử lý đơn hàng này!")
        }

        if (sellerPhones.size > 1) {
            throw RuntimeException(
                "Đơn hàng chứa sản phẩm của nhiều người bán, chưa thể cập nhật trạng thái. Vui lòng liên hệ hỗ trợ."
            )
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
        val updatedOrder = orderRepository.save(order)

        val (title, body) = when (newStatus) {
            OrderStatus.CONFIRMED -> Pair(
                "Đơn hàng #${updatedOrder.id} đã được xác nhận!",
                "Người bán đang chuẩn bị hàng cho bạn."
            )
            OrderStatus.SHIPPING -> Pair(
                "Đơn hàng #${updatedOrder.id} đang trên đường giao 🚚",
                "Sản phẩm đang được vận chuyển đến địa chỉ của bạn. Chú ý điện thoại nhận hàng nhé!"
            )
            OrderStatus.COMPLETED -> Pair(
                "Giao hàng thành công! 🎉",
                "Đơn hàng #${updatedOrder.id} đã hoàn tất. Hãy chia sẻ cảm nhận đánh giá của bạn về sản phẩm nhé!"
            )
            OrderStatus.CANCELLED -> Pair(
                "Đơn hàng #${updatedOrder.id} đã bị hủy",
                "Đơn hàng của bạn đã bị hủy bởi người bán."
            )
            else -> Pair(
                "Cập nhật đơn hàng #${updatedOrder.id}",
                "Đơn hàng của bạn đã chuyển sang trạng thái: ${newStatus.name}"
            )
        }

        notificationService.sendOrderStatusNotification(
            recipient = updatedOrder.user,
            orderId = updatedOrder.id!!,
            status = newStatus.name,
            title = title,
            body = body
        )
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
                    val hasReviewed = reviewRepository.existsByProductIdAndUserId(
                        productId = item.product.id!!,
                        userId = user.phoneNumber
                    )

                    BuyerOrderItemDTO(
                        productId = item.product.id!!,
                        productName = item.product.name,
                        farmerName = item.product.farmer.fullName,
                        quantity = item.quantity,
                        unit = item.product.unit,
                        price = item.price,
                        thumbnail = item.product.imageUrls.firstOrNull() ?: "",
                        isReviewed = hasReviewed
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

    @Transactional
    fun cancelOrderByBuyer(orderId: Long, buyerPhone: String) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { RuntimeException("Không tìm thấy đơn hàng #$orderId!") }

        if (order.user.phoneNumber != buyerPhone) {
            throw RuntimeException("Bạn không có quyền hủy đơn hàng này!")
        }

        if (order.status != "PENDING" && order.status != "UNPAID") {
            throw RuntimeException("Đơn hàng đang ở trạng thái '${order.status}', không thể tự hủy!")
        }

        val orderItems = orderItemRepository.findAllByOrder(order)
        for (item in orderItems) {
            val product = item.product
            product.quantity += item.quantity
            productRepository.save(product)
        }

        order.status = OrderStatus.CANCELLED.name
        orderRepository.save(order)
    }

    fun getFarmerRevenue(farmerPhone: String): RevenueSummaryDto {
        val farmer = userRepository.findByPhoneNumber(farmerPhone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        val totalRevenue = orderItemRepository.getTotalRevenueByFarmer(farmer) ?: 0.0

        val categoryRevenues = orderItemRepository.getRevenueByCategory(farmer)

        return RevenueSummaryDto(
            totalRevenue = totalRevenue,
            categoryRevenues = categoryRevenues
        )
    }
}