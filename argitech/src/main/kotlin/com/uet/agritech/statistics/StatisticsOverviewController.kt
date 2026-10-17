package com.uet.agritech.statistics

import com.uet.agritech.order.StatisticsOverviewService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/api/statistics")
class StatisticsOverviewController(
    private val service: StatisticsOverviewService
) {

    @GetMapping("/overview")
    fun getOverview(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?
    ): ResponseEntity<StatisticsOverviewDto> {
        val authentication = SecurityContextHolder
            .getContext()
            .authentication

        if (
            authentication == null ||
            !authentication.isAuthenticated ||
            authentication.name == "anonymousUser"
        ) {
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Bạn cần đăng nhập"
            )
        }

        val selectedYear = year ?: LocalDate.now(
            ZoneId.of("Asia/Ho_Chi_Minh")
        ).year

        return ResponseEntity.ok(
            service.getOverview(
                phone = authentication.name,
                year = selectedYear,
                month = month
            )
        )
    }
}