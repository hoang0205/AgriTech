package com.uet.agritech.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, String> {
    fun findByPhoneNumber(phoneNumber: String?): Optional<User>
    fun findByEmail(email: String): Optional<User>
}