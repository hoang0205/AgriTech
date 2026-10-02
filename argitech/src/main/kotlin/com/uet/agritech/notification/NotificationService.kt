package com.uet.agritech.notification

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    fun sendChatNotification(
        recipientId: String,
        senderName: String,
        messageText: String,
        roomId: String
    ) {
        val recipient = userRepository.findById(recipientId).orElse(null)
        if (recipient == null) {
            logger.warn("Không gửi thông báo: không tìm thấy user $recipientId")
            return
        }

        val fcmToken = recipient.fcmToken
        if (fcmToken.isNullOrBlank()) {
            logger.warn("Không gửi thông báo: user $recipientId chưa có fcm_token")
            return
        }

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
            logger.error("Gửi FCM thất bại cho user $recipientId, roomId=$roomId", e)
        }
    }

    fun sendOrderStatusNotification(
        recipient: User,
        orderId: Long,
        status: String,
        title: String,
        body: String
    ) {
        val fcmToken = recipient.fcmToken
        if (fcmToken.isNullOrBlank()) {
            logger.warn("Không gửi thông báo đơn hàng: user ${recipient.id} chưa có fcmToken")
            return
        }

        val notification = Notification.builder()
            .setTitle(title)
            .setBody(body)
            .build()

        val message = Message.builder()
            .setToken(fcmToken)
            .setNotification(notification)
            .putData("type", "ORDER_STATUS")
            .putData("orderId", orderId.toString())
            .putData("status", status)
            .putData("title", title)
            .putData("body", body)
            .build()

        try {
            FirebaseMessaging.getInstance().send(message)
            logger.info("Đã gửi FCM thông báo đơn hàng #$orderId (status=$status) tới user ${recipient.id}")
        } catch (e: Exception) {
            logger.error("Gửi FCM đơn hàng thất bại cho user ${recipient.id}, orderId=$orderId", e)
        }
    }

    fun sendOrderStatusNotification(
        recipientId: String,
        orderId: Long,
        status: String,
        title: String,
        body: String
    ) {
        val recipient = userRepository.findById(recipientId).orElse(null)
        if (recipient == null) {
            logger.warn("Không gửi thông báo đơn hàng: không tìm thấy user $recipientId")
            return
        }
        sendOrderStatusNotification(recipient, orderId, status, title, body)
    }
}