package com.uet.agritech.admin

import jakarta.persistence.EntityManager
import jakarta.persistence.Tuple
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class AdminUserStatisticsRow(
    val userId: String, val fullName: String, val phoneNumber: String, val email: String,
    var soldOrders: Long = 0, var revenue: Double = 0.0, var profit: Double = 0.0,
    var boughtOrders: Long = 0, var spending: Double = 0.0,
    var missingCostItems: Long = 0, var missingShippingOrders: Long = 0
)

data class AdminUserStatisticsPage(
    val items: List<AdminUserStatisticsRow>, val totalElements: Int,
    val page: Int, val size: Int, val from: String, val to: String
)

data class AdminUserOrderAmounts(
    val orderId: Long, val sellerId: String, val buyerId: String,
    val revenue: Double, val cost: Double, val missingCosts: Long,
    val merchandiseTotal: Double, val shippingFee: Double, val shippingCost: Double?
)

internal fun summarizeUserOrders(users: List<AdminUserStatisticsRow>, orders: List<AdminUserOrderAmounts>): List<AdminUserStatisticsRow> {
    val byId = users.associateBy { it.userId }
    val sellerOrders = mutableSetOf<Pair<String, Long>>()
    val buyerOrders = mutableSetOf<Pair<String, Long>>()
    for (order in orders) {
        // A product filter receives only its proportional share of order-level delivery amounts.
        val share = if (order.merchandiseTotal > 0) (order.revenue / order.merchandiseTotal).coerceIn(0.0, 1.0) else 0.0
        byId[order.sellerId]?.let { row ->
            if (sellerOrders.add(order.sellerId to order.orderId)) row.soldOrders++
            row.revenue += order.revenue
            row.missingCostItems += order.missingCosts
            if (order.shippingCost == null) row.missingShippingOrders++
            row.profit += order.revenue - order.cost + (order.shippingFee - (order.shippingCost ?: 0.0)) * share
        }
        byId[order.buyerId]?.let { row ->
            if (buyerOrders.add(order.buyerId to order.orderId)) row.boughtOrders++
            row.spending += order.revenue + order.shippingFee * share
        }
    }
    return users
}

@Repository
class AdminUserStatisticsRepository(private val em: EntityManager) {
    fun summarize(from: LocalDate, to: LocalDate, keyword: String, productId: String?): List<AdminUserStatisticsRow> {
        val users = em.createQuery("""
            SELECT u.id AS id, u.fullName AS name, u.phoneNumber AS phone, u.email AS email
            FROM User u WHERE LOCATE(:keyword, LOWER(u.fullName)) > 0 OR LOCATE(:keyword, LOWER(u.email)) > 0 OR LOCATE(:keyword, u.phoneNumber) > 0
        """.trimIndent(), Tuple::class.java)
            .setParameter("keyword", keyword.lowercase())
            .resultList.map { AdminUserStatisticsRow(it.get("id", String::class.java), it.get("name", String::class.java), it.get("phone", String::class.java), it.get("email", String::class.java)) }
        val rows = em.createQuery("""
            SELECT o.id AS orderId, p.farmer.id AS sellerId, o.user.id AS buyerId,
                SUM(i.quantity * i.price) AS revenue,
                SUM(i.quantity * COALESCE(i.costPrice, 0.0)) AS cost,
                SUM(CASE WHEN i.costPrice IS NULL OR i.costPrice < 0 THEN 1 ELSE 0 END) AS missingCosts,
                o.totalAmount AS total, o.shippingFee AS shipping, o.actualShippingCost AS expense
            FROM OrderItem i JOIN i.order o JOIN i.product p
            WHERE o.status = 'COMPLETED' AND o.orderDate >= :start AND o.orderDate < :end
                ${if (productId != null) "AND p.id = :productId" else ""}
            GROUP BY o.id, p.farmer.id, o.user.id, o.totalAmount, o.shippingFee, o.actualShippingCost
        """.trimIndent(), Tuple::class.java)
            .setParameter("start", from.atStartOfDay()).setParameter("end", to.plusDays(1).atStartOfDay())
            .apply { if (productId != null) setParameter("productId", productId) }
            .resultList.map { row ->
                fun number(key: String) = row.get(key, Number::class.java).toDouble()
                AdminUserOrderAmounts(row.get("orderId", Number::class.java).toLong(), row.get("sellerId", String::class.java), row.get("buyerId", String::class.java),
                    number("revenue"), number("cost"), row.get("missingCosts", Number::class.java).toLong(),
                    number("total") - number("shipping"), number("shipping"), (row.get("expense") as? Number)?.toDouble())
            }
        return summarizeUserOrders(users, rows)
    }
}

@RestController
@RequestMapping("/api/admin/statistics/users")
class AdminUserStatisticsController(private val repository: AdminUserStatisticsRepository) {
    @GetMapping
    @Transactional(readOnly = true)
    fun getUsers(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "") keyword: String,
        @RequestParam(required = false) productId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(defaultValue = "revenue") sort: String,
        @RequestParam(defaultValue = "desc") direction: String
    ): AdminUserStatisticsPage {
        val end = to ?: LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
        val start = from ?: end.minusDays(29)
        if (start.year !in 2000..2100 || end.year !in 2000..2100 || ChronoUnit.DAYS.between(start, end) !in 0..365 || page < 0 || size !in 1..100 || direction !in listOf("asc", "desc"))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Bộ lọc không hợp lệ, chọn tối đa 366 ngày")
        val selector: (AdminUserStatisticsRow) -> Double? = when (sort) {
            "revenue" -> { row -> row.revenue }
            "profit" -> { row -> row.profit }
            "spending" -> { row -> row.spending }
            "soldOrders" -> { row -> row.soldOrders.toDouble() }
            "boughtOrders" -> { row -> row.boughtOrders.toDouble() }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cột sắp xếp không hợp lệ")
        }
        val rows = repository.summarize(start, end, keyword.trim(), productId?.trim()?.takeIf { it.isNotEmpty() })
        val sorted = rows.sortedWith(Comparator { a, b ->
            val av = selector(a); val bv = selector(b)
            val result = when { av == null && bv == null -> 0; av == null -> 1; bv == null -> -1
                else -> if (direction == "asc") av.compareTo(bv) else bv.compareTo(av) }
            if (result == 0) a.userId.compareTo(b.userId) else result
        })
        val offset = (page.toLong() * size).coerceAtMost(sorted.size.toLong()).toInt()
        return AdminUserStatisticsPage(sorted.drop(offset).take(size), rows.size, page, size, start.toString(), end.toString())
    }
}
