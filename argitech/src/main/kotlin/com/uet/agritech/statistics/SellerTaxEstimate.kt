package com.uet.agritech.statistics

import com.uet.agritech.order.OrderItem
import java.math.BigDecimal
import java.math.RoundingMode

/** Scenario estimate only: a recorded cost is not proof that goods were purchased for resale. */
data class SellerTaxEstimate(
    val year: Int,
    val status: String,
    val annualRevenue: Double,
    val excludedRevenue: Double,
    val qualifyingItems: Int,
    val threshold: Double,
    val vatRate: Double,
    val incomeTaxRate: Double,
    val incomeTaxReductionRate: Double,
    val vat: Double?,
    val incomeTaxBase: Double?,
    val incomeTaxBeforeReduction: Double?,
    val incomeTaxReduction: Double?,
    val incomeTax: Double?,
    val totalTax: Double?,
    val annualProfitAfterEstimatedTax: Double?
)

object SellerTaxCalculator {
    private val threshold = BigDecimal("1000000000")
    private val revenueMethodLimit = BigDecimal("3000000000")
    private val vatRate = BigDecimal("0.01")
    private val incomeTaxRate = BigDecimal("0.005")

    fun calculate(items: List<OrderItem>, year: Int, annualProfitBeforeTax: Double?): SellerTaxEstimate {
        val completed = items.filter { it.order.status == "COMPLETED" && it.order.orderDate.year == year }
        val qualifying = completed.filter {
            val cost = it.costPrice
            cost != null && cost.isFinite() && cost > 0.0
        }
        fun revenue(rows: List<OrderItem>) = rows.fold(BigDecimal.ZERO) { total, row ->
            total + BigDecimal.valueOf(row.price).multiply(BigDecimal.valueOf(row.quantity))
        }
        val annualRevenue = revenue(qualifying)
        val excluded = revenue(completed) - annualRevenue
        val supportedYear = year >= 2026
        val taxable = annualRevenue > threshold
        val revenueMethod = annualRevenue <= revenueMethodLimit
        val reductionRate = if (year in 2026..2027) BigDecimal("0.30") else BigDecimal.ZERO
        fun BigDecimal.money() = setScale(0, RoundingMode.HALF_UP)
        val vat = if (!supportedYear) null else if (taxable) (annualRevenue * vatRate).money() else BigDecimal.ZERO
        val incomeBase = if (!supportedYear || !revenueMethod) null else (annualRevenue - threshold).max(BigDecimal.ZERO)
        val incomeBefore = incomeBase?.let { (it * incomeTaxRate).money() }
        val reduction = incomeBefore?.let { (it * reductionRate).money() }
        val incomeTax = if (incomeBefore != null && reduction != null) incomeBefore - reduction else null
        val total = if (vat != null && incomeTax != null) vat + incomeTax else null
        return SellerTaxEstimate(
            year = year,
            status = when {
                !supportedYear -> "OUTSIDE_POLICY_YEAR"
                !taxable -> "BELOW_THRESHOLD"
                !revenueMethod -> "INCOME_METHOD_REQUIRED"
                else -> "ESTIMATED"
            },
            annualRevenue = annualRevenue.toDouble(), excludedRevenue = excluded.toDouble(),
            qualifyingItems = qualifying.size, threshold = threshold.toDouble(),
            vatRate = vatRate.toDouble(), incomeTaxRate = incomeTaxRate.toDouble(),
            incomeTaxReductionRate = reductionRate.toDouble(),
            vat = vat?.toDouble(), incomeTaxBase = incomeBase?.toDouble(),
            incomeTaxBeforeReduction = incomeBefore?.toDouble(), incomeTaxReduction = reduction?.toDouble(),
            incomeTax = incomeTax?.toDouble(), totalTax = total?.toDouble(),
            annualProfitAfterEstimatedTax = if (total != null) annualProfitBeforeTax?.let { it - total.toDouble() } else null
        )
    }
}
