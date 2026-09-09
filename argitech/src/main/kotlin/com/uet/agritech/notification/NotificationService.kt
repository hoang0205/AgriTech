package com.uet.agritech.notification

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.uet.agritech.user.UserRepository
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val userRepository: UserRepository
) {

    fun sendChatNotification(
        recipientId: String,
        senderName: String,
        messageText: String,
        roomId: String
    ) {
        val recipient = userRepository.findById(recipientId).orElse(null) ?: return
        val fcmToken = recipient.fcmToken ?: return

        val notification = Notification.builder()
            .setTitle(senderName)
            .setBody(messageText)
            .build()

        val message = Message.builder()
            .setToken(fcmToken)
            .setNotification(notification)
            .putData("roomId", roomId)
            .putData("senderName", senderName)
            .build()

        try {
            FirebaseMessaging.getInstance().send(message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}