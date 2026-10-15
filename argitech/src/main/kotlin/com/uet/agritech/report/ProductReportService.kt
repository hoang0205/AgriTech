package com.uet.agritech.report

import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ProductReportService(
    private val reportRepository: ProductReportRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun create(
        phone: String,
        request: CreateProductReportRequest
    ): ProductReportResponse {
        val description = request.description.trim()

        require(description.length in 10..2000) {
            "Mô tả phải có từ 10 đến 2000 ký tự"
        }

        require(request.imageUrls.size <= 3) {
            "Chỉ được đính kèm tối đa 3 ảnh"
        }

        require(request.imageUrls.all {
            it.startsWith("https://") && it.length <= 2048
        }) {
            "Đường dẫn ảnh không hợp lệ"
        }

        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow {
                RuntimeException("Người dùng không tồn tại")
            }

        val product = productRepository.findForReport(request.productId)
            .orElseThrow {
                RuntimeException("Sản phẩm không tồn tại")
            }

        require(product.farmer.id != user.id) {
            "Không thể báo cáo sản phẩm của chính mình"
        }

        val reporterId = requireNotNull(user.id)

        val alreadyReported =
            reportRepository.existsByReporterIdAndProductIdAndStatusIn(
                reporterId,
                request.productId,
                listOf(
                    ReportStatus.PENDING,
                    ReportStatus.REVIEWING
                )
            )

        require(!alreadyReported) {
            "Bạn đã có báo cáo đang chờ xử lý cho sản phẩm này"
        }

        val report = ProductReport(
            reporterId = reporterId,
            productId = requireNotNull(product.id),
            productName = product.name,
            reason = request.reason,
            description = description,
            imageUrls = request.imageUrls.distinct()
        )

        return toResponse(reportRepository.save(report))
    }

    @Transactional(readOnly = true)
    fun getMine(
        phone: String,
        page: Int,
        size: Int
    ): Page<ProductReportResponse> {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow {
                RuntimeException("Người dùng không tồn tại")
            }

        return reportRepository.findByReporterId(
            requireNotNull(user.id),
            pageable(page, size)
        ).map { toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getAll(
        page: Int,
        size: Int,
        status: ReportStatus? = null
    ): Page<ProductReportResponse> {
        val pageable = pageable(page, size)

        val reports = if (status == null) {
            reportRepository.findAll(pageable)
        } else {
            reportRepository.findByStatus(status, pageable)
        }

        return reports.map { toResponse(it) }
    }

    @Transactional
    fun update(
        id: Long,
        request: UpdateProductReportRequest
    ): ProductReportResponse {
        val report = reportRepository.findForUpdate(id)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Báo cáo không tồn tại"
                )
            }

        if (
            report.status == ReportStatus.RESOLVED ||
            report.status == ReportStatus.REJECTED
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Báo cáo đã được đóng, không thể thay đổi kết quả"
            )
        }

        require(request.status != ReportStatus.PENDING) {
            "Không thể chuyển báo cáo về trạng thái chờ xử lý"
        }

        val reply = request.adminReply
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        require(reply == null || reply.length <= 2000) {
            "Phản hồi không được quá 2000 ký tự"
        }

        val isClosing =
            request.status == ReportStatus.RESOLVED ||
                    request.status == ReportStatus.REJECTED

        require(!isClosing || reply != null) {
            "Cần nhập phản hồi khi giải quyết hoặc từ chối báo cáo"
        }

        report.status = request.status

        if (reply != null) {
            report.adminReply = reply
        }

        return toResponse(reportRepository.save(report))
    }

    @Transactional(readOnly = true)
    fun eligibility(
        phone: String,
        productId: String
    ): ReportEligibilityResponse {
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow {
                RuntimeException("Người dùng không tồn tại")
            }

        val product = productRepository.findById(productId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Sản phẩm không tồn tại"
                )
            }

        if (product.farmer.id == user.id) {
            return ReportEligibilityResponse(
                canReport = false,
                message = "Không thể báo cáo sản phẩm của chính mình"
            )
        }

        val alreadyReported =
            reportRepository.existsByReporterIdAndProductIdAndStatusIn(
                requireNotNull(user.id),
                productId,
                listOf(
                    ReportStatus.PENDING,
                    ReportStatus.REVIEWING
                )
            )

        if (alreadyReported) {
            return ReportEligibilityResponse(
                canReport = false,
                message = "Bạn đã có báo cáo đang chờ xử lý cho sản phẩm này"
            )
        }

        return ReportEligibilityResponse(canReport = true)
    }

    private fun pageable(page: Int, size: Int): PageRequest {
        require(page >= 0) {
            "Trang không hợp lệ"
        }
        require(size in 1..50) {
            "Số lượng mỗi trang phải từ 1 đến 50"
        }

        return PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            )
        )
    }

    private fun toResponse(
        report: ProductReport
    ): ProductReportResponse {
        return ProductReportResponse(
            id = requireNotNull(report.id),
            productId = report.productId,
            productName = report.productName,
            reason = report.reason,
            description = report.description,
            imageUrls = report.imageUrls.toList(),
            status = report.status,
            adminReply = report.adminReply,
            createdAt = report.createdAt.toString(),
            productHidden = productRepository.findById(report.productId)
                .orElse(null)
                ?.hidden
        )
    }

    @Transactional
    fun updateProductVisibility(
        id: Long,
        request: UpdateProductVisibilityRequest
    ): ProductReportResponse {
        val hidden = requireNotNull(request.hidden) {
            "Cần chọn trạng thái hiển thị sản phẩm"
        }

        val report = reportRepository.findById(id)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Báo cáo không tồn tại"
                )
            }

        val product = productRepository.findForStockUpdate(report.productId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Sản phẩm không tồn tại"
                )
            }

        product.hidden = hidden
        productRepository.save(product)

        return toResponse(report)
    }
}