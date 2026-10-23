package com.uet.agritech.admin

import com.uet.agritech.order.Order
import com.uet.agritech.product.Product
import com.uet.agritech.report.ReportStatus
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import jakarta.persistence.EntityManager
import jakarta.persistence.Tuple
import java.time.LocalDate
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface AdminUserRepository : JpaRepository<User, String> {

    @Query(
        """
        SELECT u FROM User u
        WHERE u.role IN :roles
        AND (
            :keyword = ''
            OR LOCATE(:keyword, LOWER(u.fullName)) > 0
            OR LOCATE(:keyword, LOWER(u.email)) > 0
            OR LOCATE(:keyword, u.phoneNumber) > 0
        )
        """
    )
    fun searchForAdmin(
        @Param("keyword") keyword: String,
        @Param("roles") roles: Collection<Role>,
        pageable: Pageable
    ): Page<User>
}

interface AdminProductRepository : JpaRepository<Product, String> {

    @EntityGraph(attributePaths = ["farmer"])
    @Query(
        """
        SELECT p FROM Product p
        WHERE (:category IS NULL OR p.category = :category)
          AND (:hidden IS NULL OR p.hidden = :hidden)
          AND (
              :keyword = ''
              OR LOCATE(:keyword, LOWER(p.name)) > 0
              OR LOCATE(:keyword, LOWER(p.farmer.fullName)) > 0
          )
        """
    )
    fun searchForAdmin(
        @Param("keyword") keyword: String,
        @Param("category") category: String?,
        @Param("hidden") hidden: Boolean?,
        pageable: Pageable
    ): Page<Product>
}

interface AdminOrderRepository : JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = ["user"])
    @Query(
        """
        SELECT o FROM Order o
        WHERE (:status IS NULL OR o.status = :status)
          AND (
              :keyword = ''
              OR o.id = :orderId
              OR LOCATE(:keyword, LOWER(o.user.fullName)) > 0
              OR LOCATE(:keyword, o.phoneNumber) > 0
          )
        """
    )
    fun searchForAdmin(
        @Param("keyword") keyword: String,
        @Param("orderId") orderId: Long?,
        @Param("status") status: String?,
        pageable: Pageable
    ): Page<Order>
}

@Repository
class AdminOverviewRepository(
    private val entityManager: EntityManager
) {

    fun getOverview(): AdminOverviewResponse {
        val users = entityManager.createQuery(
            "SELECT COUNT(u) FROM User u WHERE u.role <> :role",
            Long::class.javaObjectType
        )
            .setParameter("role", Role.ADMIN)
            .singleResult

        val visibleProducts = entityManager.createQuery(
            "SELECT COUNT(p) FROM Product p WHERE p.hidden = false",
            Long::class.javaObjectType
        ).singleResult

        val orders = entityManager.createQuery(
            "SELECT COUNT(o) FROM Order o",
            Long::class.javaObjectType
        ).singleResult

        val completedOrders = entityManager.createQuery(
            "SELECT COUNT(o) FROM Order o WHERE o.status = :status",
            Long::class.javaObjectType
        )
            .setParameter("status", "COMPLETED")
            .singleResult

        val completedRevenue = entityManager.createQuery(
            """
            SELECT COALESCE(SUM(o.totalAmount - o.shippingFee), 0.0)
            FROM Order o
            WHERE o.status = :status
            """.trimIndent(),
            Double::class.javaObjectType
        )
            .setParameter("status", "COMPLETED")
            .singleResult

        val pendingReports = entityManager.createQuery(
            """
            SELECT COUNT(r) FROM ProductReport r
            WHERE r.status = :status
            """.trimIndent(),
            Long::class.javaObjectType
        )
            .setParameter("status", ReportStatus.PENDING)
            .singleResult

        val reviewingReports = entityManager.createQuery(
            """
            SELECT COUNT(r) FROM ProductReport r
            WHERE r.status = :status
            """.trimIndent(),
            Long::class.javaObjectType
        )
            .setParameter("status", ReportStatus.REVIEWING)
            .singleResult

        return AdminOverviewResponse(
            users = users,
            visibleProducts = visibleProducts,
            orders = orders,
            completedOrders = completedOrders,
            completedRevenue = completedRevenue,
            pendingReports = pendingReports,
            reviewingReports = reviewingReports
        )
    }
}

@Repository
class AdminStatisticsRepository(
    private val entityManager: EntityManager
) {

    fun getDaily(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<AdminDailyStatistics> {
        val rows = entityManager.createQuery(
            """
            SELECT
                YEAR(o.orderDate) AS y,
                MONTH(o.orderDate) AS m,
                DAY(o.orderDate) AS d,
                COUNT(o) AS orders,
                SUM(
                    CASE WHEN o.status = 'COMPLETED'
                    THEN o.totalAmount ELSE 0.0 END
                ) AS revenue
            FROM Order o
            WHERE o.orderDate >= :start
              AND o.orderDate < :end
            GROUP BY
                YEAR(o.orderDate),
                MONTH(o.orderDate),
                DAY(o.orderDate)
            ORDER BY
                YEAR(o.orderDate),
                MONTH(o.orderDate),
                DAY(o.orderDate)
            """.trimIndent(),
            Tuple::class.java
        )
            .setParameter("start", start)
            .setParameter("end", end)
            .resultList

        return rows.map { row ->
            val date = LocalDate.of(
                row.get("y", Number::class.java).toInt(),
                row.get("m", Number::class.java).toInt(),
                row.get("d", Number::class.java).toInt()
            )

            AdminDailyStatistics(
                date = date.toString(),
                orders = row.get("orders", Number::class.java).toLong(),
                revenue = row.get("revenue", Number::class.java).toDouble()
            )
        }
    }

    fun getStatuses(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<AdminStatusStatistics> {
        val rows = entityManager.createQuery(
            """
            SELECT o.status AS status, COUNT(o) AS count
            FROM Order o
            WHERE o.orderDate >= :start
              AND o.orderDate < :end
            GROUP BY o.status
            ORDER BY o.status
            """.trimIndent(),
            Tuple::class.java
        )
            .setParameter("start", start)
            .setParameter("end", end)
            .resultList

        return rows.map { row ->
            AdminStatusStatistics(
                status = row.get("status", String::class.java),
                count = row.get("count", Number::class.java).toLong()
            )
        }
    }

    fun getTopProducts(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<AdminTopProductStatistics> {
        val rows = entityManager.createQuery(
            """
            SELECT
                p.id AS productId,
                p.name AS productName,
                p.unit AS unit,
                SUM(i.quantity) AS quantity,
                SUM(i.quantity * i.price) AS revenue
            FROM OrderItem i
            JOIN i.product p
            JOIN i.order o
            WHERE o.status = 'COMPLETED'
              AND o.orderDate >= :start
              AND o.orderDate < :end
            GROUP BY p.id, p.name, p.unit
            ORDER BY SUM(i.quantity * i.price) DESC, p.id ASC
            """.trimIndent(),
            Tuple::class.java
        )
            .setParameter("start", start)
            .setParameter("end", end)
            .setMaxResults(10)
            .resultList

        return rows.map { row ->
            AdminTopProductStatistics(
                productId = row.get("productId", String::class.java),
                productName = row.get("productName", String::class.java),
                unit = row.get("unit", String::class.java),
                quantity = row.get("quantity", Number::class.java).toDouble(),
                revenue = row.get("revenue", Number::class.java).toDouble()
            )
        }
    }
}
