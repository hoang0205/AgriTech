package com.uet.agritech.config

import com.uet.agritech.user.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            message = ex.message ?: "Có lỗi xảy ra, vui lòng thử lại sau!"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(io.jsonwebtoken.JwtException::class)
    fun handleJwtException(
        ex: io.jsonwebtoken.JwtException
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(
                status = 401,
                message = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn"
            )
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(ex.statusCode).body(
            ErrorResponse(
                status = ex.statusCode.value(),
                message = ex.reason ?: "Yêu cầu không hợp lệ"
            )
        )
    }
}