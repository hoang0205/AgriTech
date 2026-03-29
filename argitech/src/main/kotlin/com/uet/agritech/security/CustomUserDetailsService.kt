package com.uet.agritech.security

import com.uet.agritech.user.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(private val userRepository: UserRepository) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val myUser = userRepository.findByPhoneNumber(username)
            .orElseThrow { UsernameNotFoundException("Không thấy SĐT: $username") }

        return org.springframework.security.core.userdetails.User
            .withUsername(myUser.phoneNumber)
            .password(myUser.password)
            .authorities(myUser.role.name)
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(false)
            .build()
    }
}