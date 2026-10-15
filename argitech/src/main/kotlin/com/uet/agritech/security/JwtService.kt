package com.uet.agritech.security

import com.uet.agritech.user.User
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret-base64}")
    secretBase64: String
) {
    private val signingKey: SecretKey =
        Keys.hmacShaKeyFor(
            Decoders.BASE64.decode(secretBase64)
        )

    private fun getSignInKey(): SecretKey = signingKey

    fun generateAccessToken(user: User): String {
        val now = System.currentTimeMillis()

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(user.phoneNumber)
            .claim("token_type", "ACCESS")
            .claim("token_version", user.tokenVersion)
            .claim("role", user.role.name)
            .claim("userId", user.id)
            .issuedAt(Date(now))
            .expiration(Date(now + 60 * 60 * 1000L))
            .signWith(getSignInKey())
            .compact()
    }

    fun generateRefreshToken(user: User): String {
        val now = System.currentTimeMillis()

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(user.phoneNumber)
            .claim("token_type", "REFRESH")
            .claim("token_version", user.tokenVersion)
            .issuedAt(Date(now))
            .expiration(Date(now + 7 * 24 * 60 * 60 * 1000L))
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

    fun extractPhoneNumberForType(
        token: String,
        expectedType: String
    ): String {
        return Jwts.parser()
            .verifyWith(getSignInKey())
            .require("token_type", expectedType)
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

    fun getTokenExpiration(token: String): Date {
        return Jwts.parser()
            .verifyWith(getSignInKey())
            .build()
            .parseSignedClaims(token)
            .payload
            .expiration
    }

    fun validateTokenForUser(
        token: String,
        user: User,
        expectedType: String
    ) {
        val claims = Jwts.parser()
            .verifyWith(getSignInKey())
            .require("token_type", expectedType)
            .build()
            .parseSignedClaims(token)
            .payload

        val tokenVersion =
            (claims["token_version"] as? Number)?.toLong()

        if (
            claims.subject != user.phoneNumber ||
            tokenVersion == null ||
            tokenVersion != user.tokenVersion
        ) {
            throw JwtException("Phiên đăng nhập đã hết hiệu lực")
        }
    }
}