package com.uet.agritech.user

import com.uet.agritech.security.JwtService
import org.springframework.stereotype.Service
import java.util.*

@Service
class TokenBlacklistService(
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val jwtService: JwtService
) {

    fun blacklistToken(token: String) {
        if (isTokenBlacklisted(token)) return

        try {
            val phoneNumber = jwtService.extractPhoneNumber(token)
            val expirationTime = jwtService.getTokenExpiration(token)

            tokenBlacklistRepository.save(
                TokenBlacklist(
                    token = token,
                    phoneNumber = phoneNumber,
                    expiresAt = expirationTime
                )
            )
        } catch (_: io.jsonwebtoken.ExpiredJwtException) {
        }
    }

    fun isTokenBlacklisted(token: String): Boolean {
        return tokenBlacklistRepository.findByToken(token).isPresent
    }

    fun cleanupExpiredTokens() {
        val allBlacklistedTokens = tokenBlacklistRepository.findAll()
        val now = Date()
        allBlacklistedTokens.forEach {
            if (it.expiresAt.before(now)) {
                tokenBlacklistRepository.delete(it)
            }
        }
    }
}

