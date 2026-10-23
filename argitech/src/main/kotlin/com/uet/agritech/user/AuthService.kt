package com.uet.agritech.user

import com.google.firebase.auth.FirebaseAuth
import com.uet.agritech.security.JwtService
import com.uet.agritech.user.dto.*
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val emailService: EmailService,
    private val tokenBlacklistService: TokenBlacklistService,
    private val entityManager: EntityManager,
    private val firebaseSessionService: FirebaseSessionService,
    private val registrationVerificationService: RegistrationVerificationService,
) {

    private fun normalizePhone(phone: String): String {
        val trimmed = phone.trim()
        return if (trimmed.startsWith("+84")) "0" + trimmed.removePrefix("+84") else trimmed
    }

    private fun normalizeRegistration(request: RegisterRequest): RegisterRequest {
        val phone = normalizePhone(request.phoneNumber)
        val normalized = request.copy(
            fullName = request.fullName.trim(),
            phoneNumber = phone,
            email = request.email.trim().lowercase(Locale.ROOT),
            otp = request.otp.trim()
        )
        require(normalized.role != Role.ADMIN) { "Không được đăng ký tài khoản quản trị viên" }
        require(normalized.fullName.isNotBlank()) { "Vui lòng nhập họ tên" }
        require(normalized.phoneNumber.matches(Regex("0[35789][0-9]{8}"))) {
            "Số điện thoại di động Việt Nam không hợp lệ (10 số, bắt đầu bằng 03, 05, 07, 08 hoặc 09)"
        }
        require(normalized.email.length <= 254 && normalized.email.matches(Regex("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+"))) {
            "Địa chỉ email không hợp lệ"
        }
        require(normalized.password.matches(Regex("""^(?=.*[A-Za-z])(?=.*[0-9])\S{8,}$"""))) {
            "Mật khẩu phải từ 8 kí tự, bao gồm số và chữ"
        }
        if (userRepository.findByPhoneNumber(normalized.phoneNumber).isPresent) {
            throw RuntimeException("Số điện thoại đã tồn tại!")
        }
        if (userRepository.findByEmail(normalized.email).isPresent) {
            throw RuntimeException("Email này đã được sử dụng bởi tài khoản khác!")
        }
        return normalized
    }

    fun requestRegistrationCode(request: RegisterRequest): MessageResponse {
        val normalized = normalizeRegistration(request)
        registrationVerificationService.sendCode(normalized.email)
        return MessageResponse("Mã đăng ký đã gửi tới 23021558@vnu.edu.vn. Mã có hiệu lực trong 5 phút.")
    }

    fun register(request: RegisterRequest): RegisterResponse {
        val normalized = normalizeRegistration(request)
        val encodedPassword = passwordEncoder.encode(normalized.password).toString()
        registrationVerificationService.consumeCode(normalized.email, normalized.otp)
        val user = User(
            fullName = normalized.fullName,
            phoneNumber = normalized.phoneNumber,
            password = encodedPassword,
            email = normalized.email,
            role = normalized.role
        )
        userRepository.save(user)
        return RegisterResponse("Xác minh email và đăng ký tài khoản thành công")
    }

    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByPhoneForAuthUpdate(
            normalizePhone(request.phoneNumber)
        ).orElseThrow {
            RuntimeException("Sai số điện thoại hoặc mật khẩu!")
        }

        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE)

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw RuntimeException("Sai số điện thoại hoặc mật khẩu!")
        }

        if (user.locked) {
            throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên."
            )
        }

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = jwtService.generateRefreshToken(user)
        val firebaseToken = firebaseSessionService.createLoginToken(user)

        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            fullName = user.fullName,
            avatarUrl = user.avatarUrl,
            role = user.role,
            firebaseToken = firebaseToken
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

    @Transactional
    fun resetPassword(
        request: ResetPasswordRequest
    ): MessageResponse {
        val user = userRepository.findByEmailForAuthUpdate(request.email)
            .orElseThrow {
                RuntimeException("Email không tồn tại!")
            }

        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE)

        val storedOtp = user.resetOtp
        val expiryTime = user.otpExpiryTime

        if (request.otp.isBlank() ||
            storedOtp.isNullOrBlank() ||
            storedOtp != request.otp
        ) {
            throw RuntimeException("OTP không hợp lệ!")
        }

        if (expiryTime == null || !expiryTime.after(Date())) {
            throw RuntimeException("OTP đã hết hạn!")
        }

        if (request.newPassword.isBlank()) {
            throw RuntimeException("Mật khẩu mới không được để trống!")
        }

        user.password = passwordEncoder.encode(request.newPassword).toString()

        user.tokenVersion += 1

        user.resetOtp = null
        user.otpExpiryTime = null

        userRepository.save(user)
        firebaseSessionService.revokeSessions(user)
        return MessageResponse("Đổi mật khẩu thành công!")
    }

    @Transactional
    fun refreshAccessToken(
        request: RefreshTokenRequest
    ): LoginResponse {
        val phoneNumber = jwtService.extractPhoneNumberForType(
            request.refreshToken,
            "REFRESH"
        )

        val user = userRepository.findByPhoneForAuthUpdate(phoneNumber)
            .orElseThrow {
                JwtException("Phiên đăng nhập không hợp lệ")
            }

        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE)
        if (user.locked) {
            throw JwtException("Tài khoản đã bị khóa")
        }

        if (
            tokenBlacklistService.isTokenBlacklisted(
                request.refreshToken
            )
        ) {
            throw JwtException("Refresh token đã được sử dụng hoặc thu hồi")
        }

        jwtService.validateTokenForUser(
            token = request.refreshToken,
            user = user,
            expectedType = "REFRESH"
        )

        tokenBlacklistService.blacklistToken(request.refreshToken)

        return LoginResponse(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = jwtService.generateRefreshToken(user),
            fullName = user.fullName,
            avatarUrl = user.avatarUrl,
            role = user.role
        )
    }

    @Transactional
    fun logout(request: LogoutRequest): LogoutResponse {
        val refreshPhone = jwtService.extractPhoneNumberForType(
            request.refreshToken,
            "REFRESH"
        )

        val user = userRepository.findByPhoneForAuthUpdate(refreshPhone)
            .orElseThrow {
                JwtException("Tài khoản không tồn tại")
            }

        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE)

        val accessPhone = try {
            jwtService.extractPhoneNumberForType(
                request.accessToken,
                "ACCESS"
            )
        } catch (e: ExpiredJwtException) {
            if (e.claims["token_type"] != "ACCESS") {
                throw JwtException(
                    "Loại token không hợp lệ"
                )
            }
            e.claims.subject
        }

        if (accessPhone != refreshPhone) {
            throw JwtException(
                "Hai token không thuộc cùng tài khoản"
            )
        }

        tokenBlacklistService.blacklistToken(request.refreshToken)
        tokenBlacklistService.blacklistToken(request.accessToken)

        user.fcmToken = null

        return LogoutResponse("Đăng xuất thành công!")
    }

    private fun createFirebaseToken(userId: String?): String? {
        return userId?.let {
            FirebaseAuth.getInstance().createCustomToken(it)
        }
    }
}