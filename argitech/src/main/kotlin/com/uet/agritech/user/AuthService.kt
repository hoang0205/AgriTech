package com.uet.agritech.user

import com.uet.agritech.security.JwtService
import com.uet.agritech.user.dto.*
import io.jsonwebtoken.Jwts
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val emailService: EmailService
) {

    fun register(request: RegisterRequest): String {
        if (userRepository.findByPhoneNumber(request.phoneNumber).isPresent) {
            throw java.lang.RuntimeException("Số điện thoại đã tồn tại!")
        }

        val user = User(
            fullName = request.fullName,
            phoneNumber = request.phoneNumber,
            password = passwordEncoder.encode(request.password).toString(),
            email = request.email,
            role = request.role
        )
        userRepository.save(user)
        return "Đăng ký thành công!"
    }

    fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByPhoneNumber(request.phoneNumber)
            .orElseThrow { RuntimeException("Sai số điện thoại hoặc mật khẩu!") }

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw RuntimeException("Sai số điện thoại hoặc mật khẩu!")
        }
        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = jwtService.generateRefreshToken(user)

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    fun requestPasswordReset(request: ForgotPasswordRequest): String {
        val user = userRepository.findByPhoneNumber(request.email)
            .orElseThrow { RuntimeException("Email không tồn tại!") }

        val otp = String.format("%06d", Random().nextInt(999999))
        user.resetOtp = otp
        user.otpExpiryTime = Date(System.currentTimeMillis() + 5 * 60 * 1000)

        userRepository.save(user)
        emailService.sendOtpEmail(user.email, otp)

        return "Mã OTP đã được gửi!"
    }

    fun resetPassword(request: ResetPasswordRequest): String {
        val user = userRepository.findByPhoneNumber(request.phoneNumber)
            .orElseThrow { RuntimeException("Số điện thoại không tồn tại!") }

        if (user.resetOtp != request.otp) {
            throw RuntimeException("OTP không hợp lệ!")
        }
        if (user.otpExpiryTime?.before(Date()) == true) {
            throw RuntimeException("OTP đã hết hạn!")
        }

        user.password = passwordEncoder.encode(request.newPassword).toString()
        user.resetOtp = null
        user.otpExpiryTime = null
        userRepository.save(user)

        return "Đổi mật khẩu thành công!"
    }

    fun refreshAccessToken(request: RefreshTokenRequest): LoginResponse {
        if (jwtService.isTokenExpired(request.refreshToken)) {
            throw RuntimeException("Refresh Token đã hết hạn, vui lòng đăng nhập lại!")
        }

        val phoneNumber = jwtService.extractPhoneNumber(request.refreshToken)
        val user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow { RuntimeException("Người dùng không tồn tại!") }

        val newAccessToken = jwtService.generateAccessToken(user)
        val newRefreshToken = jwtService.generateRefreshToken(user)

        return LoginResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken
        )
    }
}