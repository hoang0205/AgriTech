package com.uet.agritech.config.province

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProvinceRepository : JpaRepository<Province, String> {
}