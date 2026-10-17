package com.uet.agritech.order.dto

data class ProductProfitDto(
    val productId: String,
    val productName: String,
    val unit: String,
    val soldQuantity: Double,
    val revenue: Double,
    val knownCostRevenue: Double,
    val totalCost: Double,
    val grossProfit: Double,
    val missingCostItems: Int
)

data class ProfitSummaryDto(
    val totalRevenue: Double,
    val knownCostRevenue: Double,
    val totalCost: Double,
    val grossProfit: Double,
    val missingCostItems: Int,
    val products: List<ProductProfitDto>
)