package com.uet.agritech.user

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AdminUserRepository : JpaRepository<User, String> {

    @Query(
        """
        SELECT u FROM User u
        WHERE u.role IN :roles
        AND (
            :keyword = ''
            OR LOCATE(:keyword, LOWER(u.fullName)) > 0
            OR LOCATE(:keyword, LOWER(u.email)) > 0
            OR LOCATE(:keyword, u.phoneNumber) > 0
        )
        """
    )
    fun searchForAdmin(
        @Param("keyword") keyword: String,
        @Param("roles") roles: Collection<Role>,
        pageable: Pageable
    ): Page<User>
}