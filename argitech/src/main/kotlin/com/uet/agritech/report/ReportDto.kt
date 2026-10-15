package com.uet.agritech.report

data class CreateProductReportRequest(
    val productId: String,
    val reason: ReportReason,
    val description: String,
    val imageUrls: List<String> = emptyList()
)

data class ProductReportResponse(
    val id: Long,
    val productId: String,
    val productName: String,
    val reason: ReportReason,
    val description: String,
    val imageUrls: List<String>,
    val status: ReportStatus,
    val adminReply: String?,
    val createdAt: String,
    val productHidden: Boolean? = null
)

data class UpdateProductReportRequest(
    val status: ReportStatus,
    val adminReply: String? = null
)

data class ReportEligibilityResponse(
    val canReport: Boolean,
    val message: String? = null
)

data class UpdateProductVisibilityRequest(
    val hidden: Boolean? = null
)