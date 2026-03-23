package com.uet.agritech.security // Nhớ check lại dòng package này cho khớp với project của mày

import com.uet.agritech.user.User
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService {

    private val secretKey = "DayLaChuoiBiMatCuaAppNongNghiepUetDungDeMaHoaTokenNhe123456"

    private fun getSignInKey(): SecretKey {
        val keyBytes = secretKey.toByteArray()
        return Keys.hmacShaKeyFor(keyBytes)
    }

    fun generateAccessToken(user: User): String {
        return Jwts.builder()
            .subject(user.phoneNumber)
            .claim("role", user.role.name)
            .claim("userId", user.id)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + 1000 * 60 * 60))
            .signWith(getSignInKey())
            .compact()
    }

    fun generateRefreshToken(user: User): String {
        return Jwts.builder()
            .subject(user.phoneNumber)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 7))
            .signWith(getSignInKey())
            .compact()
    }

    fun extractPhoneNumber(token: String): String {
        return Jwts.parser()
            .verifyWith(getSignInKey())
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
    }

    fun isTokenExpired(token: String): Boolean {
        val expiration = Jwts.parser()
            .verifyWith(getSignInKey())
            .build()
            .parseSignedClaims(token)
            .payload
            .expiration
        return expiration.before(Date())
    }
}