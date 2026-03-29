package com.uet.agritech.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtFilter(
    private val jwtService: JwtService,
    private val userDetailsService: CustomUserDetailsService,
    private val tokenBlacklistService: com.uet.agritech.user.TokenBlacklistService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = authHeader.substring(7)

        try {
            if (tokenBlacklistService.isTokenBlacklisted(jwt)) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.writer.write("Token đã bị logout")
                return
            }

            val phoneNumber = jwtService.extractPhoneNumber(jwt)

            if (phoneNumber != null && SecurityContextHolder.getContext().authentication == null) {

                val userDetails = userDetailsService.loadUserByUsername(phoneNumber)

                if (!jwtService.isTokenExpired(jwt)) {
                    val authToken = UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.authorities
                    )

                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authToken
                }
            }
            filterChain.doFilter(request, response)

        } catch (e: io.jsonwebtoken.ExpiredJwtException) {
            println("Token đã hết hạn!")
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.writer.write("Token has expired")
        } catch (e: Exception) {
            println("Lỗi JWT Filter: ${e.message}")
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.writer.write("Invalid Token")
        }
    }
}