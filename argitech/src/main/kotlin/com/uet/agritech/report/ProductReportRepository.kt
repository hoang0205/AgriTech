package com.uet.agritech.report

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductReportRepository :
    JpaRepository<ProductReport, Long> {

    fun existsByReporterIdAndProductIdAndStatusIn(
        reporterId: String,
        productId: String,
        statuses: Collection<ReportStatus>
    ): Boolean

    fun findByReporterId(
        reporterId: String,
        pageable: Pageable
    ): Page<ProductReport>
}