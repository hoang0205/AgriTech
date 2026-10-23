package com.uet.agritech.admin

import java.util.Date

data class AdminProductResponse(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val quantity: Double,
    val unit: String,
    val description: String,
    val imageUrls: List<String>,
    val farmerId: String,
    val farmerName: String,
    val hidden: Boolean
)

data class AdminProductPageResponse(
    val items: List<AdminProductResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)

data class AdminProductVisibilityRequest(
    val hidden: Boolean? = null
)

data class AdminProductVisibilityResponse(
    val id: String,
    val hidden: Boolean
)


data class AdminOrderResponse(
    val id: String,
    val buyerName: String,
    val phoneNumber: String,
    val shippingAddress: String,
    val totalAmount: Double,
    val status: String,
    val orderDate: String,
    val products: List<AdminOrderProductPreviewResponse> = emptyList()
)

data class AdminOrderPageResponse(
    val items: List<AdminOrderResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)

data class AdminOrderItemResponse(
    val id: String,
    val productId: String,
    val productName: String,
    val farmerName: String,
    val quantity: Double,
    val unit: String,
    val price: Double,
    val subtotal: Double
)

data class AdminOrderDetailResponse(
    val order: AdminOrderResponse,
    val items: List<AdminOrderItemResponse>
)

data class AdminOrderProductPreviewResponse(
    val id: String,
    val productName: String,
    val quantity: Double,
    val unit: String,
    val thumbnail: String?
)

data class AdminOverviewResponse(
    val users: Long,
    val visibleProducts: Long,
    val orders: Long,
    val completedOrders: Long,
    val completedRevenue: Double,
    val pendingReports: Long,
    val reviewingReports: Long
)

data class AdminDailyStatistics(
    val date: String,
    val orders: Long,
    val revenue: Double
)

data class AdminStatusStatistics(
    val status: String,
    val count: Long
)

data class AdminTopProductStatistics(
    val productId: String,
    val productName: String,
    val unit: String,
    val quantity: Double,
    val revenue: Double
)

data class AdminStatisticsResponse(
    val from: String,
    val to: String,
    val totalOrders: Long,
    val completedOrders: Long,
    val completedRevenue: Double,
    val daily: List<AdminDailyStatistics>,
    val statuses: List<AdminStatusStatistics>,
    val topProducts: List<AdminTopProductStatistics>
)

data class AdminUserResponse(
    val id: String,
    val fullName: String,
    val phoneNumber: String,
    val email: String,
    val role: String,
    val createdAt: Date?,
    val locked: Boolean
)

data class AdminUserPageResponse(
    val items: List<AdminUserResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)

data class UpdateUserStatusRequest(
    val locked: Boolean? = null
)
