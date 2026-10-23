package com.uet.agritech.user

import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailService(private val mailSender: JavaMailSender) {

    fun sendRegistrationOtpEmail(accountEmail: String, otp: String) {
        val message = SimpleMailMessage()
        message.from = "doanhoang670@gmail.com"
        // Demo recipient is fixed; accountEmail remains the identity being registered.
        message.setTo("23021558@vnu.edu.vn")
        message.subject = "[AgriTech] Mã xác minh đăng ký tài khoản"
        message.text = """
            Bạn đang đăng ký tài khoản AgriTech với email: $accountEmail
            Mã xác minh của bạn là: $otp
            Mã có hiệu lực trong 5 phút và chỉ dùng một lần.
        """.trimIndent()
        mailSender.send(message)
    }

    fun sendOtpEmail(toEmail: String, otp: String) {
        val message = SimpleMailMessage()
        message.from = "doanhoang670@gmail.com"
        message.setTo(toEmail)
        message.subject = "[AgriTech] Mã xác nhận lấy lại mật khẩu"
        message.text = """
            Chào bạn,
            
            Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản AgriTech.
            Mã OTP của bạn là: $otp
            
            Mã này có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai!
        """.trimIndent()

        mailSender.send(message)
    }
}