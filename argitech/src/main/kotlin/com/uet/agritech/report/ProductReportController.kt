package com.uet.agritech.report

import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/reports")
class ProductReportController(
    private val reportService: ProductReportService
) {
    @PostMapping("/products")
    fun create(
        authentication: Authentication,
        @RequestBody request: CreateProductReportRequest
    ): ProductReportResponse {
        return reportService.create(
            authentication.name,
            request
        )
    }

    @GetMapping("/products/{productId}/eligibility")
    fun eligibility(
        authentication: Authentication,
        @PathVariable("productId") productId: String
    ): ReportEligibilityResponse {
        return reportService.eligibility(
            authentication.name,
            productId
        )
    }

    @GetMapping("/mine")
    fun getMine(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ) = reportService.getMine(
        authentication.name,
        page,
        size
    )
}

@RestController
@RequestMapping("/api/admin/reports")
class AdminReportController(
    private val reportService: ProductReportService
) {
    @GetMapping
    fun getAll(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) status: ReportStatus? = null
    ) = reportService.getAll(page, size, status)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable("id") id: Long,
        @RequestBody request: UpdateProductReportRequest
    ): ProductReportResponse {
        return reportService.update(id, request)
    }
}