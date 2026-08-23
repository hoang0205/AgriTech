package com.uet.agritech.websocket

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

@Controller
class ChatController(
    private val messagingTemplate: SimpMessagingTemplate,
    private val chatMessageRepository: ChatMessageRepository
) {
    @MessageMapping("/chat")
    fun processMessage(@Payload chatMessage: ChatMessage) {
        val savedMsg = chatMessageRepository.save(chatMessage)

        messagingTemplate.convertAndSendToUser(
            chatMessage.receiverId,
            "/queue/messages",
            savedMsg
        )
    }
}