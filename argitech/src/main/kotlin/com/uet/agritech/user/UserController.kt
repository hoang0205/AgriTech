package com.uet.agritech.user

import com.uet.agritech.user.dto.MessageResponse
import com.uet.agritech.user.dto.UpdateProfileRequest
import com.uet.agritech.user.dto.UserProfileResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService,
    private val userRepository: UserRepository
) {

    @GetMapping("/profile")
    fun getProfile(): UserProfileResponse {
        val phone = SecurityContextHolder.getContext().authentication?.name
        val user = userRepository.findByPhoneNumber(phone)
            .orElseThrow { RuntimeException("User không tồn tại") }

        return UserProfileResponse(
            fullName = user.fullName,
            avatarUrl = user.avatarUrl ?: ""
        )
    }

    @PutMapping("/profile")
    fun updateProfile(@RequestBody request: UpdateProfileRequest): MessageResponse {
        val phone = SecurityContextHolder.getContext().authentication?.name
        userService.updateProfile(phone.toString(), request)
        return MessageResponse("Cập nhật thông tin thành công!")
    }
}