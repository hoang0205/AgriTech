package com.uet.agritech.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ShippingAddressRepository : JpaRepository<ShippingAddress, Long> {

    fun findAllByUser(user: User): List<ShippingAddress>

    fun findByUserAndIsDefaultTrue(user: User): ShippingAddress?
}