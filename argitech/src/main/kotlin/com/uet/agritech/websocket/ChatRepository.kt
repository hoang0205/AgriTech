package com.uet.agritech.websocket

import org.springframework.data.jpa.repository.JpaRepository

interface ChatMessageRepository : JpaRepository<ChatMessage, String> {
    fun findBySenderIdAndReceiverIdOrReceiverIdAndSenderIdOrderByTimestampAsc(
        sender1: String, receiver1: String,
        receiver2: String, sender2: String
    ): List<ChatMessage>
}