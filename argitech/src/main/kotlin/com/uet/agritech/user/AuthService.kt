package com.uet.agritech.user

import com.uet.agritech.security.JwtService
import com.uet.agritech.user.dto.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val emailService: EmailService,
    private val tokenBlacklistService: TokenBlacklistService
) {

    fun register(request: RegisterRequest): RegisterResponse {
        if (userRepository.findByPhoneNumber(request.phoneNumber).isPresent) {
            throw java.lang.RuntimeException("Số điện thoại đã tồn tại!")
        }

        if (userRepository.findByEmail(request.email).isPresent) {
            throw RuntimeException("Email này đã được sử dụng bởi tài khoản khác!")
        }

        val user = User(
            fullName = request.fullName,
            phoneNumber = request.phoneNumber,
            password = passwordEncoder.encode(request.password).toString(),
            email = request.email,
            role = request.role
        )
        userRepository.save(user)
        return RegisterResponse("Đăng kí tài khoản thành công")
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

    fun requestPasswordReset(request: ForgotPasswordRequest): MessageResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { RuntimeException("Email không tồn tại!") }

        val otp = String.format("%06d", Random().nextInt(999999))
        user.resetOtp = otp
        user.otpExpiryTime = Date(System.currentTimeMillis() + 5 * 60 * 1000)

        userRepository.save(user)
        emailService.sendOtpEmail(user.email, otp)

        return MessageResponse("Mã OTP đã được gửi đến email của bạn!")
    }

    fun verifyEmail(request: VerifyEmailRequest): MessageResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { RuntimeException("Email không tồn tại!") }

        if (user.resetOtp != request.otp) {
            throw RuntimeException("OTP không hợp lệ!")
        }
        if (user.otpExpiryTime?.before(Date()) == true) {
            throw RuntimeException("OTP đã hết hạn!")
        }

        return MessageResponse("Xác thực email thành công")
    }

    fun resetPassword(request: ResetPasswordRequest): MessageResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { RuntimeException("Email không tồn tại!") }

        user.password = passwordEncoder.encode(request.newPassword).toString()
        user.resetOtp = null
        user.otpExpiryTime = null
        userRepository.save(user)

        return MessageResponse("Đổi mật khẩu thành công!")
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

    fun logout(request: LogoutRequest): LogoutResponse {
        try {
            tokenBlacklistService.blacklistToken(request.accessToken)
            return LogoutResponse("Đăng xuất thành công!")
        } catch (e: Exception) {
            throw RuntimeException("Lỗi đăng xuất: ${e.message}")
        }
    }
}