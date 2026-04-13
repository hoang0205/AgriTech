package com.uet.agritech.config.province

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WardRepository : JpaRepository<Ward, String> {

    fun findAllByProvince(province: Province): List<Ward>

    fun findAllByProvinceCode(provinceCode: String): List<Ward>
}