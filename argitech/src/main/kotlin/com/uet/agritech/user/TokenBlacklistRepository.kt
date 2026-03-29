package com.uet.agritech.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TokenBlacklistRepository : JpaRepository<TokenBlacklist, Long> {
    fun findByToken(token: String): Optional<TokenBlacklist>
    fun findByPhoneNumber(phoneNumber: String): List<TokenBlacklist>
}

