package com.uet.agritech.websocket

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "chat_messages")
data class ChatMessage(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)