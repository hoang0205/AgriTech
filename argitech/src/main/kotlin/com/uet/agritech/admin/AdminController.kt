package com.uet.agritech.admin

import com.uet.agritech.order.Order
import com.uet.agritech.order.OrderItemRepository
import com.uet.agritech.order.dto.OrderStatus
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.FirebaseSessionService
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController(
    private val repository: AdminUserRepository,
    private val entityManager: EntityManager,
    private val firebaseSessionService: FirebaseSessionService,
    private val auditService: AdminAuditService
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun getUsers(
        @RequestParam(defaultValue = "") keyword: String,
        @RequestParam(required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): AdminUserPageResponse {
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Trang phải từ 0 và số dòng phải từ 1 đến 100"
            )
        }

        val roles = when (role?.trim()?.uppercase(Locale.ROOT)) {
            null, "" -> listOf(Role.ADMIN, Role.BUYER, Role.FARMER)
            "ADMIN" -> listOf(Role.ADMIN)
            "USER" -> listOf(Role.BUYER, Role.FARMER)
            else -> throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Vai trò không hợp lệ"
            )
        }

        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            )
        )

        val result = repository.searchForAdmin(
            keyword = keyword.trim().lowercase(Locale.ROOT),
            roles = roles,
            pageable = pageable
        )

        return AdminUserPageResponse(
            items = result.content.map { toResponse(it) },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size
        )
    }

    @PatchMapping("/{id}/status")
    @Transactional
    fun updateStatus(
        @PathVariable id: String,
        @RequestBody request: UpdateUserStatusRequest
    ): AdminUserResponse {
        val locked = request.locked
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cần truyền trạng thái locked"
            )

        val user = repository.findById(id).orElseThrow {
            ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy người dùng"
            )
        }

        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE)

        if (user.role == Role.ADMIN) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Không được thay đổi trạng thái tài khoản Admin"
            )
        }

        if (user.locked == locked) {
            return toResponse(user)
        }

        val previousLocked = user.locked
        user.locked = locked

        if (locked) {
            user.tokenVersion += 1
            user.fcmToken = null
        }

        repository.saveAndFlush(user)

        if (locked) {
            firebaseSessionService.revokeSessions(user)
        }

        auditService.record(
            action = if (locked) AdminAuditAction.USER_LOCK else AdminAuditAction.USER_UNLOCK,
            targetType = AdminAuditTarget.USER,
            targetId = requireNotNull(user.id),
            targetName = user.fullName,
            beforeState = if (previousLocked) "Đã khóa" else "Hoạt động",
            afterState = if (locked) "Đã khóa" else "Hoạt động"
        )

        return toResponse(user)
    }

    private fun toResponse(user: User): AdminUserResponse {
        return AdminUserResponse(
            id = requireNotNull(user.id),
            fullName = user.fullName,
            phoneNumber = user.phoneNumber,
            email = user.email,
            role = if (user.role == Role.ADMIN) "ADMIN" else "USER",
            createdAt = user.createdAt,
            locked = user.locked
        )
    }
}

@RestController
@RequestMapping("/api/admin/products")
class AdminProductController(
    private val repository: AdminProductRepository,
    private val productRepository: ProductRepository,
    private val auditService: AdminAuditService
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun getProducts(
        @RequestParam(defaultValue = "") keyword: String,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) hidden: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): AdminProductPageResponse {
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Trang phải từ 0 và số dòng phải từ 1 đến 100"
            )
        }

        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.asc("name"),
                Sort.Order.asc("id")
            )
        )

        val result = repository.searchForAdmin(
            keyword = keyword.trim().lowercase(Locale.ROOT),
            category = category?.trim()?.takeIf { it.isNotEmpty() },
            hidden = hidden,
            pageable = pageable
        )

        return AdminProductPageResponse(
            items = result.content.map { product ->
                AdminProductResponse(
                    id = requireNotNull(product.id),
                    name = product.name,
                    category = product.category,
                    price = product.price,
                    quantity = product.quantity,
                    unit = product.unit,
                    description = product.description,
                    imageUrls = product.imageUrls.toList(),
                    farmerId = requireNotNull(product.farmer.id),
                    farmerName = product.farmer.fullName,
                    hidden = product.hidden
                )
            },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size
        )
    }

    @PatchMapping("/{id}/visibility")
    @Transactional
    fun updateVisibility(
        @PathVariable id: String,
        @RequestBody request: AdminProductVisibilityRequest
    ): AdminProductVisibilityResponse {
        val hidden = request.hidden
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cần truyền trạng thái hidden"
            )

        val product = productRepository.findForStockUpdate(id)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy sản phẩm"
                )
            }

        val previousHidden = product.hidden
        product.hidden = hidden
        productRepository.save(product)

        if (previousHidden != hidden) {
            auditService.record(
                action = if (hidden) AdminAuditAction.PRODUCT_HIDE else AdminAuditAction.PRODUCT_SHOW,
                targetType = AdminAuditTarget.PRODUCT,
                targetId = requireNotNull(product.id),
                targetName = product.name,
                beforeState = if (previousHidden) "Đang ẩn" else "Đang hiển thị",
                afterState = if (hidden) "Đang ẩn" else "Đang hiển thị"
            )
        }

        return AdminProductVisibilityResponse(
            id = requireNotNull(product.id),
            hidden = product.hidden
        )
    }
}

@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController(
    private val repository: AdminOrderRepository,
    private val itemRepository: OrderItemRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun getOrders(
        @RequestParam(defaultValue = "") keyword: String,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): AdminOrderPageResponse {
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Trang phải từ 0 và số dòng phải từ 1 đến 100"
            )
        }

        val normalizedStatus = status
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }

        if (
            normalizedStatus != null &&
            OrderStatus.entries.none { it.name == normalizedStatus }
        ) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Trạng thái đơn hàng không hợp lệ"
            )
        }

        val normalizedKeyword = keyword.trim().lowercase(Locale.ROOT)

        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.desc("orderDate"),
                Sort.Order.desc("id")
            )
        )

        val result = repository.searchForAdmin(
            keyword = normalizedKeyword,
            orderId = normalizedKeyword.removePrefix("#").toLongOrNull(),
            status = normalizedStatus,
            pageable = pageable
        )

        val orderIds = result.content.map { requireNotNull(it.id) }

        val itemsByOrder = if (orderIds.isEmpty()) {
            emptyMap()
        } else {
            itemRepository.findForAdminOrderIds(orderIds)
                .groupBy { requireNotNull(it.order.id) }
        }

        return AdminOrderPageResponse(
            items = result.content.map { order ->
                val products = itemsByOrder[order.id].orEmpty().map { item ->
                    AdminOrderProductPreviewResponse(
                        id = requireNotNull(item.id).toString(),
                        productName = item.product.name,
                        quantity = item.quantity,
                        unit = item.product.unit,
                        thumbnail = item.product.imageUrls.firstOrNull()
                    )
                }

                toResponse(order, products)
            },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size
        )
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    fun getDetail(@PathVariable id: Long): AdminOrderDetailResponse {
        val order = repository.findById(id).orElseThrow {
            ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy đơn hàng"
            )
        }

        val items = itemRepository.findAllByOrder(order)

        return AdminOrderDetailResponse(
            order = toResponse(order),
            items = items.map { item ->
                AdminOrderItemResponse(
                    id = requireNotNull(item.id).toString(),
                    productId = requireNotNull(item.product.id),
                    productName = item.product.name,
                    farmerName = item.product.farmer.fullName,
                    quantity = item.quantity,
                    unit = item.product.unit,
                    price = item.price,
                    subtotal = item.price * item.quantity
                )
            }
        )
    }

    private fun toResponse(
        order: Order,
        products: List<AdminOrderProductPreviewResponse> = emptyList()
    ): AdminOrderResponse {
        return AdminOrderResponse(
            id = requireNotNull(order.id).toString(),
            buyerName = order.user.fullName,
            phoneNumber = order.phoneNumber,
            shippingAddress = order.shippingAddress,
            totalAmount = order.totalAmount,
            status = order.status,
            orderDate = order.orderDate.toString(),
            products = products
        )
    }
}

@RestController
@RequestMapping("/api/admin/overview")
class AdminOverviewController(
    private val repository: AdminOverviewRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun getOverview(): AdminOverviewResponse {
        return repository.getOverview()
    }
}

@RestController
@RequestMapping("/api/admin/statistics")
class AdminStatisticsController(
    private val repository: AdminStatisticsRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun getStatistics(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?
    ): AdminStatisticsResponse {
        val endDate = to ?: LocalDate.now(
            ZoneId.of("Asia/Ho_Chi_Minh")
        )
        val startDate = from ?: endDate.minusDays(29)

        val daysBetween = ChronoUnit.DAYS.between(startDate, endDate)

        if (
            daysBetween !in 0L..365L ||
            startDate.year !in 2000..2100 ||
            endDate.year !in 2000..2100
        ) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Chọn khoảng ngày hợp lệ, tối đa 366 ngày, trong năm 2000–2100"
            )
        }

        val start = startDate.atStartOfDay()
        val end = endDate.plusDays(1).atStartOfDay()

        val dailyMap = repository.getDaily(start, end)
            .associateBy { it.date }

        val daily = (0L..daysBetween).map { offset ->
            val date = startDate.plusDays(offset).toString()

            dailyMap[date] ?: AdminDailyStatistics(
                date = date,
                orders = 0,
                revenue = 0.0
            )
        }

        val statuses = repository.getStatuses(start, end)

        return AdminStatisticsResponse(
            from = startDate.toString(),
            to = endDate.toString(),
            totalOrders = statuses.sumOf { it.count },
            completedOrders = statuses
                .firstOrNull { it.status == "COMPLETED" }
                ?.count ?: 0,
            completedRevenue = daily.sumOf { it.revenue },
            daily = daily,
            statuses = statuses,
            topProducts = repository.getTopProducts(start, end)
        )
    }
}
