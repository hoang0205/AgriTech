package com.uet.agritech.report

import jakarta.persistence.*
import java.time.LocalDateTime

enum class ReportReason {
    SCAM,
    MISLEADING_INFORMATION,
    INAPPROPRIATE_CONTENT,
    OTHER
}

enum class ReportStatus {
    PENDING,
    REVIEWING,
    RESOLVED,
    REJECTED
}

@Entity
@Table(name = "product_reports")
class ProductReport(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val reporterId: String,

    @Column(nullable = false)
    val productId: String,

    @Column(nullable = false)
    val productName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val reason: ReportReason,

    @Column(nullable = false, length = 2000)
    val description: String,

    @ElementCollection
    @CollectionTable(
        name = "product_report_images",
        joinColumns = [JoinColumn(name = "report_id")]
    )
    @Column(name = "image_url", length = 2048)
    val imageUrls: List<String> = emptyList(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ReportStatus = ReportStatus.PENDING,

    @Column(length = 2000)
    var adminReply: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)