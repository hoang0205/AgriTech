package com.uet.agritech.admin

import com.uet.agritech.user.AdminUserRepository
import com.uet.agritech.user.FirebaseSessionService
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.Date
import java.util.Locale

data class AdminUserResponse(
    val id: String,
    val fullName: String,
    val phoneNumber: String,
    val email: String,
    val role: String,
    val createdAt: Date?,
    val locked: Boolean
)

data class AdminUserPageResponse(
    val items: List<AdminUserResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)

data class UpdateUserStatusRequest(
    val locked: Boolean? = null
)

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController(
    private val repository: AdminUserRepository,
    private val entityManager: EntityManager,
    private val firebaseSessionService: FirebaseSessionService
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun getUsers(
        @RequestParam(defaultValue = "") keyword: String,
        @RequestParam(required = false) role: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): AdminUserPageResponse {
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Trang phải từ 0 và số dòng phải từ 1 đến 100"
            )
        }

        val roles = when (role?.trim()?.uppercase(Locale.ROOT)) {
            null, "" -> listOf(Role.ADMIN, Role.BUYER, Role.FARMER)
            "ADMIN" -> listOf(Role.ADMIN)
            "USER" -> listOf(Role.BUYER, Role.FARMER)
            else -> throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Vai trò không hợp lệ"
            )
        }

        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            )
        )

        val result = repository.searchForAdmin(
            keyword = keyword.trim().lowercase(Locale.ROOT),
            roles = roles,
            pageable = pageable
        )

        return AdminUserPageResponse(
            items = result.content.map { toResponse(it) },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.number,
            size = result.size
        )
    }

    @PatchMapping("/{id}/status")
    @Transactional
    fun updateStatus(
        @PathVariable id: String,
        @RequestBody request: UpdateUserStatusRequest
    ): AdminUserResponse {
        val locked = request.locked
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cần truyền trạng thái locked"
            )

        val user = repository.findById(id).orElseThrow {
            ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy người dùng"
            )
        }

        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE)

        if (user.role == Role.ADMIN) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Không được thay đổi trạng thái tài khoản Admin"
            )
        }

        if (user.locked == locked) {
            return toResponse(user)
        }

        user.locked = locked

        if (locked) {
            user.tokenVersion += 1
            user.fcmToken = null
        }

        repository.saveAndFlush(user)

        if (locked) {
            firebaseSessionService.revokeSessions(user)
        }

        return toResponse(user)
    }

    private fun toResponse(user: User): AdminUserResponse {
        return AdminUserResponse(
            id = requireNotNull(user.id),
            fullName = user.fullName,
            phoneNumber = user.phoneNumber,
            email = user.email,
            role = if (user.role == Role.ADMIN) "ADMIN" else "USER",
            createdAt = user.createdAt,
            locked = user.locked
        )
    }
}