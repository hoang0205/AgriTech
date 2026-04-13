package com.uet.agritech.config.province

import jakarta.persistence.*

@Entity
@Table(name = "provinces")
class Province(
    @Id val code: String,
    val name: String,
    val type: String
)

@Entity
@Table(name = "wards")
class Ward(
    @Id val code: String,
    val name: String,
    val type: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code")
    val province: Province
)