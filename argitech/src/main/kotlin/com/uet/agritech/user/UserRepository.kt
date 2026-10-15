package com.uet.agritech.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface UserRepository : JpaRepository<User, String> {

    fun findByPhoneNumber(phoneNumber: String?): Optional<User>

    fun findByEmail(email: String): Optional<User>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.phoneNumber = :phone")
    fun findByPhoneForAuthUpdate(
        @Param("phone") phone: String
    ): Optional<User>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    fun findByEmailForAuthUpdate(
        @Param("email") email: String
    ): Optional<User>

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true
    )
    @Query("""
    UPDATE User u
    SET u.fcmToken = null,
        u.rowVersion = u.rowVersion + 1
    WHERE u.fcmToken = :token
      AND u.phoneNumber <> :phone
""")
    fun clearFcmTokenFromOtherUsers(
        @Param("token") token: String,
        @Param("phone") phone: String
    ): Int
}