package com.uet.agritech.user

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "token_blacklist")
class TokenBlacklist(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(columnDefinition = "LONGTEXT")
    val token: String,

    val phoneNumber: String,

    val blacklistedAt: Date = Date(),

    val expiresAt: Date
)

