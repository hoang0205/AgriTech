package com.uet.agritech.report

import com.uet.agritech.admin.AdminAuditService
import com.uet.agritech.notification.NotificationService
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertFailsWith

class ProductReportNotificationTest {
    private val reports = mockk<ProductReportRepository>()
    private val products = mockk<ProductRepository>()
    private val users = mockk<UserRepository>()
    private val notifications = mockk<NotificationService>(relaxed = true)
    private val service = ProductReportService(reports, products, users,
        mockk<AdminAuditService>(relaxed = true), notifications)
    private val report = ProductReport(id = 7, reporterId = "reporter", productId = "p1",
        productName = "Rau", reason = ReportReason.OTHER, description = "Nội dung báo cáo")

    init {
        every { reports.findForUpdate(7) } returns Optional.of(report)
        every { reports.save(any()) } answers { firstArg() }
        every { users.findById("reporter") } returns Optional.empty()
        every { products.findById("p1") } returns Optional.empty()
    }

    @Test
    fun `each status change notifies the reporter with the reply`() {
        for (status in listOf(ReportStatus.REVIEWING, ReportStatus.RESOLVED, ReportStatus.REJECTED)) {
            report.status = ReportStatus.PENDING
            report.adminReply = null
            service.update(7, UpdateProductReportRequest(status, "  Đã kiểm tra  "))
            verify(exactly = 1) {
                notifications.sendReportStatusNotification("reporter", 7, status.name, "Rau", "Đã kiểm tra")
            }
        }
    }

    @Test
    fun `unchanged review does not duplicate notification but a new reply does`() {
        report.status = ReportStatus.REVIEWING
        service.update(7, UpdateProductReportRequest(ReportStatus.REVIEWING))
        verify(exactly = 0) { notifications.sendReportStatusNotification(any(), any(), any(), any(), any()) }
        service.update(7, UpdateProductReportRequest(ReportStatus.REVIEWING, "Đang xác minh"))
        verify(exactly = 1) {
            notifications.sendReportStatusNotification("reporter", 7, "REVIEWING", "Rau", "Đang xác minh")
        }
    }

    @Test
    fun `invalid closing and already closed reports do not notify`() {
        assertFailsWith<IllegalArgumentException> { service.update(7, UpdateProductReportRequest(ReportStatus.RESOLVED)) }
        report.status = ReportStatus.RESOLVED
        assertFailsWith<ResponseStatusException> { service.update(7, UpdateProductReportRequest(ReportStatus.REJECTED, "Từ chối")) }
        verify(exactly = 0) { notifications.sendReportStatusNotification(any(), any(), any(), any(), any()) }
    }
}
