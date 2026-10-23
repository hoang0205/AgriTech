package com.uet.agritech.order

import com.uet.agritech.cart.CartItemRepository
import com.uet.agritech.delivery.DeliveryQuoteRequest
import com.uet.agritech.delivery.DeliveryQuoteService
import com.uet.agritech.delivery.DeliveryStatus
import com.uet.agritech.notification.NotificationService
import com.uet.agritech.order.dto.BuyerOrderItemDTO
import com.uet.agritech.order.dto.BuyerOrderResponse
import com.uet.agritech.order.dto.CheckoutRequest
import com.uet.agritech.order.dto.FarmerOrderItemDTO
import com.uet.agritech.order.dto.FarmerOrderResponse
import com.uet.agritech.order.dto.OrderStatus
import com.uet.agritech.order.dto.RevenueSummaryDto
import com.uet.agritech.product.Product
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.review.ReviewRepository
import com.uet.agritech.user.RecommendationService
import com.uet.agritech.user.ShippingAddressRepository
import com.uet.agritech.user.UserRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val interactionService: RecommendationService,
    private val reviewRepository: ReviewRepository,
    private val notificationService: NotificationService,
    private val entityManager: EntityManager,
    private val addressRepository: ShippingAddressRepository,
    private val deliveryQuoteService: DeliveryQuoteService
) {

    @Transactional
    fun checkout(
        request: CheckoutRequest,
        userPhone: String
    ): List<Order> {
        val user = userRepository.findByPhoneNumber(userPhone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        val addressId = requireNotNull(request.addressId) {
            "Vui lòng chọn địa chỉ nhận hàng."
        }

        require(addressId > 0) {
            "Địa chỉ nhận hàng không hợp lệ."
        }

        val expectedShippingFee = requireNotNull(
            request.expectedShippingFee
        ) {
            "Vui lòng lấy báo phí giao hàng trước khi đặt hàng."
        }

        require(expectedShippingFee >= 0) {
            "Phí giao hàng dự kiến không hợp lệ."
        }

        val address = addressRepository.findById(addressId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy địa chỉ nhận hàng."
                )
            }

        if (address.user.id != user.id) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Bạn chỉ được dùng địa chỉ nhận hàng của mình."
            )
        }

        entityManager.refresh(
            address,
            LockModeType.PESSIMISTIC_READ
        )

        require(
            address.receiverName.isNotBlank() &&
                    address.phoneNumber.isNotBlank() &&
                    address.addressDetail.isNotBlank()
        ) {
            "Thông tin địa chỉ nhận hàng chưa đầy đủ."
        }

        if (request.selectedCartItemIds.isEmpty()) {
            throw RuntimeException("Bạn chưa chọn món nào để thanh toán!")
        }

        val selectedIds = request.selectedCartItemIds.distinct().sorted()
        val cartItems = cartItemRepository.findAllForCheckout(selectedIds)

        if (cartItems.isEmpty() || cartItems.size != selectedIds.size) {
            throw RuntimeException(
                "Giỏ hàng đã thay đổi. Vui lòng tải lại trước khi đặt hàng!"
            )
        }

        for (item in cartItems) {
            entityManager.refresh(item, LockModeType.PESSIMISTIC_WRITE)

            if (item.user.phoneNumber != userPhone) {
                throw RuntimeException("Giỏ hàng không hợp lệ!")
            }

            if (!item.quantity.isFinite() || item.quantity <= 0.0) {
                throw RuntimeException("Số lượng sản phẩm không hợp lệ!")
            }
        }

        val products = lockProducts(
            cartItems.map { requireNotNull(it.product.id) }
        )

        val quantitiesByProduct = cartItems
            .groupBy { requireNotNull(it.product.id) }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }

        for ((productId, quantity) in quantitiesByProduct) {
            val product = products.getValue(productId)

            require(!product.hidden) {
                "Sản phẩm đang bị ẩn, không thể mua: ${product.name}"
            }

            if (
                !quantity.isFinite() ||
                !product.quantity.isFinite() ||
                quantity > product.quantity
            ) {
                throw RuntimeException(
                    "Không đủ hàng cho sản phẩm: ${product.name}"
                )
            }
        }

        products.values
            .map { it.farmer }
            .distinctBy { it.id }
            .sortedBy { it.id }
            .forEach { seller ->
                entityManager.refresh(
                    seller,
                    LockModeType.PESSIMISTIC_READ
                )
            }

        val deliveryQuote = deliveryQuoteService.quote(
            phone = userPhone,
            request = DeliveryQuoteRequest(
                addressId = addressId,
                selectedCartItemIds = selectedIds
            )
        )

        val unavailableSellers = deliveryQuote.sellers.filter {
            it.status != DeliveryStatus.AVAILABLE
        }

        if (
            unavailableSellers.isNotEmpty() ||
            !deliveryQuote.allDeliverable ||
            deliveryQuote.totalShippingFee == null
        ) {
            val details = unavailableSellers.joinToString("; ") {
                "${it.sellerName}: ${it.message}"
            }

            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                details.ifBlank {
                    "Chưa xác định được phí giao hàng."
                }
            )
        }

        if (deliveryQuote.totalShippingFee != expectedShippingFee) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Phí giao hàng đã thay đổi. Vui lòng cập nhật báo phí trước khi đặt hàng."
            )
        }

        val deliveryBySeller = deliveryQuote.sellers.associateBy {
            it.sellerId
        }

        val itemsBySeller = cartItems.groupBy {
            products.getValue(requireNotNull(it.product.id)).farmer.phoneNumber
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
            val seller = products.getValue(
                requireNotNull(sellerCartItems.first().product.id)
            ).farmer

            val sellerTotal = sellerCartItems.sumOf {
                products.getValue(requireNotNull(it.product.id)).price * it.quantity
            }

            val sellerDelivery = deliveryBySeller[
                requireNotNull(seller.id)
            ] ?: throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Thiếu báo phí giao hàng của người bán."
            )

            val shippingFee = requireNotNull(
                sellerDelivery.shippingFee
            ) {
                "Chưa xác định được phí giao hàng của người bán."
            }

            val newOrder = Order(
                user = user,
                totalAmount = sellerTotal + shippingFee.toDouble(),
                shippingAddress = address.addressDetail,
                phoneNumber = address.phoneNumber,
                shippingFee = shippingFee,
                pickupAddress = seller.pickupAddress,
                pickupLatitude = seller.pickupLatitude,
                pickupLongitude = seller.pickupLongitude,
                deliveryLatitude = address.latitude,
                deliveryLongitude = address.longitude,
                receiverName = address.receiverName,
                paymentMethod = if (isVnpay) "VNPAY" else "COD",
                deliveryDistanceKm = sellerDelivery.distanceKm
            )
            newOrder.status = initialStatus

            val savedOrder = orderRepository.save(newOrder)

            val orderItems = sellerCartItems.map { cartItem ->
                val product = products.getValue(requireNotNull(cartItem.product.id))

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
                    price = product.price,
                    costPrice = product.costPrice
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

    private fun lockProducts(productIds: List<String>): Map<String, Product> {
        return productIds.distinct().sorted().associateWith { productId ->
            val product = productRepository.findForStockUpdate(productId)
                .orElseThrow {
                    RuntimeException("Sản phẩm không còn tồn tại: $productId")
                }

            entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE)
            product
        }
    }

    private fun restoreStock(orderItems: List<OrderItem>) {
        val products = lockProducts(
            orderItems.map { requireNotNull(it.product.id) }
        )

        val quantities = orderItems
            .groupBy { requireNotNull(it.product.id) }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }

        for ((productId, quantity) in quantities) {
            val product = products.getValue(productId)
            product.quantity += quantity
            productRepository.save(product)
        }
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
                receiverName = order.receiverName,
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
                totalRevenueFromThisOrder = items.sumOf {
                    it.price * it.quantity
                },
                shippingFee = order.shippingFee,
                pickupAddress = order.pickupAddress,
                ghnOrderCode = order.ghnOrderCode,
                ghnStatus = order.ghnStatus,
                ghnShippingFee = order.ghnShippingFee,
                deliveryMethod = order.deliveryMethod?.name,
                actualShippingCost = order.actualShippingCost,
                ghnPendingCreation = order.ghnRequestJson != null && order.ghnOrderCode == null && !order.isGhnSimulation()
            )
        }.sortedByDescending { it.orderDate }
    }

    @Transactional
    fun updateStatus(orderId: Long, newStatus: OrderStatus, sellerPhone: String) {
        val order = orderRepository.findForStatusUpdate(orderId)
            .orElseThrow { RuntimeException("Không tìm thấy đơn hàng!") }

        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE)

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

        require(!order.hasActiveGhnShipment()) {
            "Đơn đã gửi GHN. Hãy cập nhật hoặc hủy vận đơn GHN trước khi đổi trạng thái thủ công."
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

        if (newStatus == OrderStatus.SHIPPING || newStatus == OrderStatus.COMPLETED) {
            // Old orders already in transit may finish using their existing flow.
            require(order.deliveryMethod == DeliveryMethod.SELF ||
                (order.deliveryMethod == null && currentStatus == OrderStatus.SHIPPING)) {
                "Vui lòng chọn Tự tổ chức giao trước khi cập nhật giao hàng thủ công."
            }
        }

        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(orderItems)
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
                shippingFee = order.shippingFee,
                pickupAddress = order.pickupAddress,
                ghnOrderCode = order.ghnOrderCode,
                ghnStatus = order.ghnStatus,
                ghnShippingFee = order.ghnShippingFee,
                ghnPendingCreation = order.ghnRequestJson != null && order.ghnOrderCode == null && !order.isGhnSimulation(),
                deliveryMethod = order.deliveryMethod?.name,
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
        val order = orderRepository.findForStatusUpdate(orderId)
            .orElseThrow {
                RuntimeException("Không tìm thấy đơn hàng #$orderId!")
            }

        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE)

        if (order.user.phoneNumber != buyerPhone) {
            throw RuntimeException("Bạn không có quyền hủy đơn hàng này!")
        }

        require(!order.hasActiveGhnShipment()) {
            "Đơn đã gửi GHN, cần xử lý vận đơn với người bán trước."
        }

        if (order.status != "PENDING" && order.status != "UNPAID") {
            throw RuntimeException("Đơn hàng đang ở trạng thái '${order.status}', không thể tự hủy!")
        }

        val orderItems = orderItemRepository.findAllByOrder(order)
        restoreStock(orderItems)

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
