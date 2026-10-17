package com.uet.agritech.order

import com.uet.agritech.order.dto.*
import com.uet.agritech.statistics.StatisticsCategoryDto
import com.uet.agritech.statistics.StatisticsMonthDto
import com.uet.agritech.statistics.StatisticsOrderDto
import com.uet.agritech.statistics.StatisticsOrderItemDto
import com.uet.agritech.statistics.StatisticsOverviewDto
import com.uet.agritech.statistics.StatisticsOverviewRepository
import com.uet.agritech.statistics.StatisticsSectionDto
import com.uet.agritech.user.Role
import com.uet.agritech.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

private enum class StatisticsKind {
    REVENUE,
    PROFIT,
    SPENDING
}

@Service
class StatisticsOverviewService(
    private val userRepository: UserRepository,
    private val statisticsRepository: StatisticsOverviewRepository
) {

    @Transactional(readOnly = true)
    fun getOverview(
        phone: String,
        year: Int,
        month: Int?
    ): StatisticsOverviewDto {
        if (year !in 1..9998) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Năm thống kê không hợp lệ"
            )
        }

        if (month != null && month !in 1..12) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Tháng phải nằm trong khoảng 1 đến 12"
            )
        }

        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tài khoản không tồn tại"
                )
            }

        val start = LocalDate.of(year, 1, 1).atStartOfDay()
        val end = start.plusYears(1)

        val buyerItems = statisticsRepository.findBuyerItems(
            buyer = user,
            start = start,
            end = end
        )

        val sellerItems = statisticsRepository.findSellerItems(
            seller = user,
            start = start,
            end = end
        )

        return StatisticsOverviewDto(
            year = year,
            selectedMonth = month,
            revenue = buildSection(
                sellerItems,
                month,
                StatisticsKind.REVENUE
            ),
            profit = buildSection(
                sellerItems,
                month,
                StatisticsKind.PROFIT
            ),
            spending = buildSection(
                buyerItems,
                month,
                StatisticsKind.SPENDING
            )
        )
    }

    private fun buildSection(
        yearlyItems: List<OrderItem>,
        selectedMonth: Int?,
        kind: StatisticsKind
    ): StatisticsSectionDto {
        val selectedItems = if (selectedMonth == null) {
            yearlyItems
        } else {
            yearlyItems.filter {
                it.order.orderDate.monthValue == selectedMonth
            }
        }

        val yearlyItemsByMonth = yearlyItems.groupBy {
            it.order.orderDate.monthValue
        }

        val monthlyAmounts = (1..12).map { month ->
            val items = yearlyItemsByMonth[month].orEmpty()

            StatisticsMonthDto(
                month = month,
                amount = totalAmount(items, kind),
                orderCount = orderCount(items),
                missingCostItems = missingCostCount(items, kind)
            )
        }

        val categories = selectedItems
            .groupBy { it.product.category }
            .map { (category, items) ->
                StatisticsCategoryDto(
                    category = category,
                    amount = items.sumOf {
                        itemAmount(it, kind) ?: 0.0
                    },
                    missingCostItems = missingCostCount(items, kind)
                )
            }
            .sortedByDescending { it.amount }

        val orders = selectedItems
            .groupBy { requireNotNull(it.order.id) }
            .map { (orderId, items) ->
                val order = items.first().order

                StatisticsOrderDto(
                    orderId = orderId,
                    orderDate = order.orderDate.toString(),
                    amount = totalAmount(items, kind),
                    missingCostItems = missingCostCount(items, kind),
                    items = items.map { item ->
                        StatisticsOrderItemDto(
                            productId = requireNotNull(item.product.id),
                            productName = item.product.name,
                            quantity = item.quantity,
                            unit = item.product.unit,
                            unitPrice = item.price,
                            amount = itemAmount(item, kind)
                        )
                    }
                )
            }
            .sortedByDescending { it.orderDate }

        val knownCostItems = selectedItems.filter {
            it.costPrice != null
        }

        return StatisticsSectionDto(
            totalAmount = totalAmount(selectedItems, kind),
            orderCount = orderCount(selectedItems),
            monthlyAmounts = monthlyAmounts,
            categories = categories,
            orders = orders,
            missingCostItems = missingCostCount(selectedItems, kind),
            knownCostRevenue = if (kind == StatisticsKind.PROFIT) {
                knownCostItems.sumOf { it.price * it.quantity }
            } else {
                0.0
            },
            totalCost = if (kind == StatisticsKind.PROFIT) {
                knownCostItems.sumOf {
                    requireNotNull(it.costPrice) * it.quantity
                }
            } else {
                0.0
            }
        )
    }

    private fun itemAmount(
        item: OrderItem,
        kind: StatisticsKind
    ): Double? {
        return when (kind) {
            StatisticsKind.REVENUE,
            StatisticsKind.SPENDING -> item.price * item.quantity

            StatisticsKind.PROFIT -> {
                item.costPrice?.let { cost ->
                    (item.price - cost) * item.quantity
                }
            }
        }
    }

    private fun totalAmount(
        items: List<OrderItem>,
        kind: StatisticsKind
    ): Double {
        return if (kind == StatisticsKind.SPENDING) {
            items.distinctBy { it.order.id }.sumOf {
                it.order.totalAmount
            }
        } else {
            items.sumOf {
                itemAmount(it, kind) ?: 0.0
            }
        }
    }

    private fun orderCount(items: List<OrderItem>): Int {
        return items.map {
            requireNotNull(it.order.id)
        }.distinct().size
    }

    private fun missingCostCount(
        items: List<OrderItem>,
        kind: StatisticsKind
    ): Int {
        return if (kind == StatisticsKind.PROFIT) {
            items.count { it.costPrice == null }
        } else {
            0
        }
    }
}