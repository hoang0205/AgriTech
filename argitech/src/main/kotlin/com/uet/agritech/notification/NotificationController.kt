package com.uet.agritech.notification

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SendChatNotificationRequest(
    val recipientId: String,
    val senderName: String,
    val messageText: String,
    val roomId: String
)

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    @PostMapping("/send-chat")
    fun sendChatNotification(
        @RequestBody request: SendChatNotificationRequest
    ): ResponseEntity<Map<String, String>> {
        notificationService.sendChatNotification(
            recipientId = request.recipientId,
            senderName = request.senderName,
            messageText = request.messageText,
            roomId = request.roomId
        )
        return ResponseEntity.ok(mapOf("message" to "Đã gửi thông báo thành công"))
    }
}