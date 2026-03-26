package com.uet.agritech.user

import com.uet.agritech.user.dto.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<RegisterResponse> {
        return ResponseEntity.ok(authService.register(request))
    }

    @PostMapping("/login")
        fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return ResponseEntity.ok(authService.login(request))
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@RequestBody request: ForgotPasswordRequest): ResponseEntity<MessageResponse> {
        return ResponseEntity.ok(authService.requestPasswordReset(request))
    }

    @PostMapping("/verify-email")
    fun verifyEmail(@RequestBody request: VerifyEmailRequest): ResponseEntity<MessageResponse> {
        return ResponseEntity.ok(authService.verifyEmail(request))
    }

    @PostMapping("/reset-password")
    fun resetPassword(@RequestBody request: ResetPasswordRequest): ResponseEntity<MessageResponse> {
        return ResponseEntity.ok(authService.resetPassword(request))
    }

    @PostMapping("/refresh-token")
    fun refresh(@RequestBody request: RefreshTokenRequest): ResponseEntity<LoginResponse> {
        return ResponseEntity.ok(authService.refreshAccessToken(request))
    }
}