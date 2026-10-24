package com.uet.agritech.config

import com.uet.agritech.user.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.dao.DataAccessException
import jakarta.persistence.PersistenceException
import org.slf4j.LoggerFactory

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DataAccessException::class, PersistenceException::class)
    fun handleDatabaseException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Database operation failed", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(status = 500, message = "Hệ thống chưa xử lý được yêu cầu. Vui lòng thử lại sau.")
        )
    }

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
