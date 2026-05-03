package com.uet.agritech.user

import com.uet.agritech.user.dto.ChangePasswordRequest
import com.uet.agritech.user.dto.MessageResponse
import com.uet.agritech.user.dto.UpdateProfileRequest
import com.uet.agritech.user.dto.UserProfileResponse
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional
    fun updateProfile(phoneNumber: String, request: UpdateProfileRequest): UserProfileResponse {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { RuntimeException("User không tồn tại") }

        request.fullName?.let { user.fullName = it }
        request.avatarUrl?.let { user.avatarUrl = it }

        userRepository.save(user)

        return UserProfileResponse(
            fullName = user.fullName,
            avatarUrl = user.avatarUrl ?: ""
        )
    }

    @Transactional
    fun changePassword(request: ChangePasswordRequest, phoneNumber: String) {
        if (request.newPassword != request.confirmPassword) {
            throw RuntimeException("Mật khẩu xác nhận không khớp!")
        }

        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { RuntimeException("Người dùng không tồn tại!") }

        if (!passwordEncoder.matches(request.oldPassword, user.password)) {
            throw RuntimeException("Mật khẩu cũ không chính xác!")
        }

        if (request.newPassword == request.oldPassword) {
            throw RuntimeException("Mật khẩu mới không được trùng với mật khẩu cũ!")
        }

        user.password = passwordEncoder.encode(request.newPassword).toString()
        userRepository.save(user)
    }
}