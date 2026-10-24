package com.uet.agritech.statistics

import com.uet.agritech.order.OrderItem

/** Missing historical cost snapshots count as zero; never read today's product cost. */
internal fun OrderItem.statisticsCostPrice(): Double = costPrice?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

data class SellerOrderFinance(
    val orderId: Long, val orderDate: String, val status: String, val deliveryMethod: String?,
    val merchandiseRevenue: Double, val costOfGoods: Double?, val grossProfit: Double?,
    val shippingCollected: Long, val actualShippingCost: Long?, val shippingMargin: Long?,
    val profitAfterShipping: Double?, val missingCostItems: Int,
    val buyerName: String? = null, val buyerAvatar: String? = null, val buyerPhone: String? = null,
    val items: List<StatisticsOrderItemDto> = emptyList()
)

data class SellerFinanceSummary(
    val merchandiseRevenue: Double, val costOfGoods: Double?, val grossProfit: Double?,
    val shippingCollected: Long, val recordedShippingCost: Long, val shippingMargin: Long?,
    val profitAfterShipping: Double?, val cancelledShippingCost: Long,
    val missingCostItems: Int, val missingShippingCostOrders: Int,
    val orders: List<SellerOrderFinance>
)

object SellerFinanceCalculator {
    fun calculate(items: List<OrderItem>, month: Int?): SellerFinanceSummary {
        val rows = items.filter {
            it.order.status in setOf("COMPLETED", "CANCELLED") &&
                (month == null || it.order.orderDate.monthValue == month)
        }.groupBy { requireNotNull(it.order.id) }.map { (id, products) ->
            val order = products.first().order
            val completed = order.status == "COMPLETED"
            val revenue = if (completed) products.sumOf { it.price * it.quantity } else 0.0
            val missing = if (completed) products.count {
                val cost = it.costPrice
                cost == null || !cost.isFinite() || cost < 0
            } else 0
            val cost = if (completed) products.sumOf { it.statisticsCostPrice() * it.quantity } else 0.0
            val gross = revenue - cost
            val collected = if (completed) order.shippingFee else 0L
            // Keep missing expenses null; provisionally treat their shipping margin as break-even.
            val expense = order.actualShippingCost
            val margin = expense?.let { collected - it } ?: 0L
            SellerOrderFinance(id, order.orderDate.toString(), order.status, order.deliveryMethod?.name,
                revenue, cost, gross, collected, expense, margin,
                gross + margin, missing,
                buyerName = order.user.fullName, buyerAvatar = order.user.avatarUrl,
                buyerPhone = order.phoneNumber,
                items = products.map { item ->
                    StatisticsOrderItemDto(requireNotNull(item.product.id), item.product.name,
                        item.quantity, item.product.unit, item.price, item.price * item.quantity,
                        item.product.imageUrls.firstOrNull())
                })
        }.sortedByDescending { it.orderDate }
        val missingGoods = rows.sumOf { it.missingCostItems }
        val profitRows = rows
        val missingShipping = profitRows.count { it.actualShippingCost == null }
        val costs = profitRows.sumOf { requireNotNull(it.costOfGoods) }
        val gross = profitRows.sumOf { requireNotNull(it.grossProfit) }
        val margin = profitRows.sumOf { requireNotNull(it.shippingMargin) }
        return SellerFinanceSummary(rows.sumOf { it.merchandiseRevenue }, costs, gross,
            rows.sumOf { it.shippingCollected }, rows.sumOf { it.actualShippingCost ?: 0L }, margin,
            gross + margin,
            rows.filter { it.status == "CANCELLED" }.sumOf { it.actualShippingCost ?: 0L },
            missingGoods, missingShipping, rows)
    }
}
