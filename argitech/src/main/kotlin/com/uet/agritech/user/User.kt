package com.uet.agritech.user

import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
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

    @Column
    var avatarUrl: String? = null,

    var resetOtp: String? = null,

    var otpExpiryTime: Date? = null,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date? = null,

    @Column(name = "fcm_token")
    var fcmToken: String? = null,

    @Column(name = "token_version", nullable = false)
    var tokenVersion: Long = 0,

    @Column(
        name = "is_locked",
        nullable = false,
        columnDefinition = "boolean default false"
    )
    var locked: Boolean = false,

    @Column(name = "pickup_address", length = 1000)
    var pickupAddress: String? = null,

    @Column(name = "pickup_latitude")
    var pickupLatitude: Double? = null,

    @Column(name = "pickup_longitude")
    var pickupLongitude: Double? = null,

    @Version
    @Column(name = "row_version", nullable = false)
    var rowVersion: Long = 0
) {
    @PrePersist
    fun onCreate() {
        createdAt = Date()
    }
}