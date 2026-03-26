package com.uet.agritech.user.dto

import com.uet.agritech.user.Role

data class RegisterRequest(
    val fullName: String,
    val phoneNumber: String,
    val email: String,
    val password: String,
    val role: Role
)

data class RegisterResponse(
    val message: String,
    val success: Boolean = true,
)

data class LoginRequest(
    val phoneNumber: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class VerifyEmailRequest(
    val email: String,
    val otp: String
)

data class ResetPasswordRequest(
    val email: String,
    val newPassword: String
)

data class ErrorResponse(
    val status: Int,
    val message: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class MessageResponse(
    val message: String,
    val success: Boolean = true
)