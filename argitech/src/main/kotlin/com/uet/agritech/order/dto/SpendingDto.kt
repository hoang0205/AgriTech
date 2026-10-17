package com.uet.agritech.order.dto

data class MonthlySpendingDto(
    val month: Int,
    val amount: Double,
    val orderCount: Int
)

data class CategorySpendingDto(
    val category: String,
    val amount: Double
)

data class SpendingSummaryDto(
    val year: Int,
    val totalSpent: Double,
    val completedOrderCount: Int,
    val averageOrderValue: Double,
    val pendingAmount: Double,
    val pendingOrderCount: Int,
    val unpaidAmount: Double,
    val unpaidOrderCount: Int,
    val cancelledOrderCount: Int,
    val monthlySpending: List<MonthlySpendingDto>,
    val categorySpending: List<CategorySpendingDto>
)