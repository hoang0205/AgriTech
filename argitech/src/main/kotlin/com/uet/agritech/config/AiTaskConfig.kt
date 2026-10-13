package com.uet.agritech.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class AiTaskConfig {
    @Bean
    fun aiSummaryExecutor() = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 1
        queueCapacity = 100
        setThreadNamePrefix("ai-summary-")
    }
}