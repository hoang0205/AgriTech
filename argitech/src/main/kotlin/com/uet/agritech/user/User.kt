package com.uet.agritech.user

import jakarta.persistence.*
import java.util.Date

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: String? = null,

    @Column(nullable = false)
    var fullName: String,

    @Column(unique = true, nullable = false)
    var phoneNumber: String,

    @Column(unique = true, nullable = false)
    var email: String,

    @Column(nullable = false)
    var password: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role,

    var resetOtp: String? = null,

    var otpExpiryTime: Date? = null,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date? = null
) {
    @PrePersist
    fun onCreate() {
        createdAt = Date()
    }
}