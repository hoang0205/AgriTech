package com.uet.agritech.report

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

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

    fun findByStatus(
        status: ReportStatus,
        pageable: Pageable
    ): Page<ProductReport>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProductReport r where r.id = :id")
    fun findForUpdate(
        @Param("id") id: Long
    ): Optional<ProductReport>
}