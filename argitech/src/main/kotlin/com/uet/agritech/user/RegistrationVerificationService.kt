package com.uet.agritech.user

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom

/** Short-lived registration challenges for the single-server demo; restart requires a new code. */
@Service
class RegistrationVerificationService(
    private val emailService: EmailService,
    private val passwordEncoder: PasswordEncoder
) {
    private data class Challenge(
        val hash: String,
        val expiresAt: Long,
        val sentAt: Long,
        var attempts: Int = 0
    )

    private val challenges = mutableMapOf<String, Challenge>()
    private val random = SecureRandom()

    @Synchronized
    fun sendCode(email: String) {
        val now = System.currentTimeMillis()
        challenges.entries.removeIf { it.value.expiresAt <= now }
        val previous = challenges[email]
        if (previous != null && now - previous.sentAt < 60_000L) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ 60 giây trước khi gửi lại mã")
        }
        val otp = random.nextInt(1_000_000).toString().padStart(6, '0')
        val hash = requireNotNull(passwordEncoder.encode(otp))
        emailService.sendRegistrationOtpEmail(email, otp)
        challenges[email] = Challenge(hash, now + 5 * 60_000L, now)
    }

    @Synchronized
    fun consumeCode(email: String, otp: String) {
        val challenge = challenges[email]
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng gửi mã xác minh đăng ký trước")
        if (challenge.expiresAt <= System.currentTimeMillis()) {
            challenges.remove(email)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã xác minh đã hết hạn. Vui lòng gửi lại mã")
        }
        if (challenge.attempts >= 5) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Đã nhập sai mã quá 5 lần. Vui lòng gửi lại mã")
        }
        if (!otp.matches(Regex("[0-9]{6}")) || !passwordEncoder.matches(otp, challenge.hash)) {
            challenge.attempts++
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã xác minh không đúng")
        }
        challenges.remove(email)
    }
}
