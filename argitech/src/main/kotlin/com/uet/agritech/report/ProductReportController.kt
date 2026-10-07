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
        @RequestParam(defaultValue = "10") size: Int
    ) = reportService.getAll(page, size)
}