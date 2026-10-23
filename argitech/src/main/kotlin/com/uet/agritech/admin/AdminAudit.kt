package com.uet.agritech.admin

import com.uet.agritech.user.Role
import com.uet.agritech.user.UserRepository
import jakarta.persistence.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Locale

enum class AdminAuditAction {
    USER_LOCK,
    USER_UNLOCK,
    PRODUCT_HIDE,
    PRODUCT_SHOW,
    REPORT_UPDATE
}

enum class AdminAuditTarget {
    USER,
    PRODUCT,
    REPORT
}

@Entity
@Table(
    name = "admin_audit_logs",
    indexes = [
        Index(name = "idx_admin_audit_time", columnList = "created_at"),
        Index(name = "idx_admin_audit_action_time", columnList = "action,created_at")
    ]
)
class AdminAuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Column(name = "actor_id", nullable = false)
    val actorId: String,

    @Column(name = "actor_name", nullable = false)
    val actorName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val action: AdminAuditAction,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    val targetType: AdminAuditTarget,

    @Column(name = "target_id", nullable = false)
    val targetId: String,

    @Column(name = "target_name", nullable = false)
    val targetName: String,

    @Column(name = "before_state", nullable = false, columnDefinition = "TEXT")
    val beforeState: String,

    @Column(name = "after_state", nullable = false, columnDefinition = "TEXT")
    val afterState: String,

    @Column(name = "related_report_id")
    val relatedReportId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

data class AdminAuditResponse(
    val id: String,
    val actorId: String,
    val actorName: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val targetName: String,
    val beforeState: String,
    val afterState: String,
    val relatedReportId: String?,
    val createdAt: String
)

data class AdminAuditPageResponse(
    val items: List<AdminAuditResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)

interface AdminAuditRepository : JpaRepository<AdminAuditLog, String> {
    @Query(
        """
        SELECT a FROM AdminAuditLog a
        WHERE (:action IS NULL OR a.action = :action)
          AND (
              :keyword = ''
              OR LOCATE(:keyword, LOWER(a.actorName)) > 0
              OR LOCATE(:keyword, LOWER(a.targetName)) > 0
              OR LOCATE(:keyword, LOWER(a.targetId)) > 0
          )
        """
    )
    fun search(
        @Param("keyword") keyword: String,
        @Param("action") action: AdminAuditAction?,
        pageable: Pageable
    ): Page<AdminAuditLog>
}

@Service
class AdminAuditService(
    private val repository: AdminAuditRepository,
    private val userRepository: UserRepository
) {
    // Bắt buộc dùng giao dịch của thao tác quản trị đang thực hiện.
    @Transactional(propagation = Propagation.MANDATORY)
    fun record(
        action: AdminAuditAction,
        targetType: AdminAuditTarget,
        targetId: String,
        targetName: String,
        beforeState: String,
        afterState: String,
        relatedReportId: String? = null
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập")

        val actor = userRepository.findByPhoneNumber(authentication.name).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại")
        }

        if (actor.role != Role.ADMIN || actor.locked) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền quản trị")
        }

        repository.saveAndFlush(
            AdminAuditLog(
                actorId = requireNotNull(actor.id),
                actorName = actor.fullName,
                action = action,
                targetType = targetType,
                targetId = targetId,
                targetName = targetName.take(255),
                beforeState = beforeState,
                afterState = afterState,
                relatedReportId = relatedReportId
            )
        )
    }
}

@RestController
@RequestMapping("/api/admin/audit-logs")
class AdminAuditController(
    private val repository: AdminAuditRepository
) {
    @GetMapping
    @Transactional(readOnly = true)
    fun getLogs(
        @RequestParam(defaultValue = "") keyword: String,
        @RequestParam(required = false) action: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): AdminAuditPageResponse {
        if (page < 0 || size !in 1..50 || keyword.length > 200) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Trang phải từ 0, số dòng từ 1 đến 50, từ khóa tối đa 200 ký tự"
            )
        }

        val actionFilter = action?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                AdminAuditAction.valueOf(it.uppercase(Locale.ROOT))
            } catch (_: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại thao tác không hợp lệ")
            }
        }

        val result = repository.search(
            keyword.trim().lowercase(Locale.ROOT),
            actionFilter,
            PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
            )
        )

        return AdminAuditPageResponse(
            items = result.content.map {
                AdminAuditResponse(
                    id = requireNotNull(it.id),
                    actorId = it.actorId,
                    actorName = it.actorName,
                    action = it.action.name,
                    targetType = it.targetType.name,
                    targetId = it.targetId,
                    targetName = it.targetName,
                    beforeState = it.beforeState,
                    afterState = it.afterState,
                    relatedReportId = it.relatedReportId,
                    createdAt = it.createdAt.toString()
                )
            },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size
        )
    }
}
