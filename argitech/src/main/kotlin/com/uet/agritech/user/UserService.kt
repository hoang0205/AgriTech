package com.uet.agritech.user

import com.uet.agritech.user.dto.MessageResponse
import com.uet.agritech.user.dto.UpdateProfileRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(private val userRepository: UserRepository) {

    @Transactional
    fun updateProfile(phoneNumber: String, request: UpdateProfileRequest): MessageResponse {
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { RuntimeException("User không tồn tại") }

        request.fullName?.let { user.fullName = it }
        request.avatarUrl?.let { user.avatarUrl = it }

        userRepository.save(user)

        return MessageResponse("Cập nhật thông tin cá nhân thành công")
    }
}