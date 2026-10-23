package com.uet.agritech.statistics

data class StatisticsMonthDto(
    val month: Int,
    val amount: Double,
    val orderCount: Int,
    val missingCostItems: Int
)

data class StatisticsCategoryDto(
    val category: String,
    val amount: Double,
    val missingCostItems: Int
)

data class StatisticsOrderItemDto(
    val productId: String,
    val productName: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double,

    val amount: Double?,
    val imageUrl: String? = null
)

data class StatisticsOrderDto(
    val orderId: Long,
    val orderDate: String,
    val amount: Double,
    val missingCostItems: Int,
    val items: List<StatisticsOrderItemDto>,
    val buyerName: String? = null,
    val buyerAvatar: String? = null,
    val buyerPhone: String? = null,
    val sellerName: String? = null,
    val sellerAvatar: String? = null,
    val sellerPhone: String? = null
)

data class StatisticsSectionDto(
    val totalAmount: Double,
    val orderCount: Int,

    val monthlyAmounts: List<StatisticsMonthDto>,

    val categories: List<StatisticsCategoryDto>,
    val orders: List<StatisticsOrderDto>,

    val missingCostItems: Int,
    val knownCostRevenue: Double,
    val totalCost: Double
)

data class StatisticsOverviewDto(
    val year: Int,
    val selectedMonth: Int?,
    val revenue: StatisticsSectionDto?,
    val profit: StatisticsSectionDto?,
    val spending: StatisticsSectionDto,
    val sellerFinance: SellerFinanceSummary? = null,
    val sellerTax: SellerTaxEstimate? = null
)
