package com.uet.agritech.notification

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class NotificationService(
    private val userRepository: UserRepository,
    private val inbox: NotificationInboxService
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    fun sendReportStatusNotification(
        recipientId: String,
        reportId: Long,
        status: String,
        productName: String,
        adminReply: String?
    ) {
        val statusText = when (status) {
            "REVIEWING" -> "đang được xem xét"
            "RESOLVED" -> "đã được giải quyết"
            "REJECTED" -> "đã bị từ chối"
            else -> return
        }
        val title = "Cập nhật báo cáo #$reportId"
        val body = "Báo cáo về sản phẩm \"$productName\" $statusText." +
            (adminReply?.takeIf { it.isNotBlank() }?.let { "\nPhản hồi: $it" } ?: "")
        val saved = inbox.recordReport(recipientId, reportId, status, title, body)
        val token = userRepository.findById(recipientId).orElse(null)?.fcmToken
        if (token.isNullOrBlank()) return

        // Keep the complete reply in the inbox and limit the push payload size.
        val message = Message.builder()
            .setToken(token)
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .putData("recipientId", recipientId)
            .putData("type", "REPORT_STATUS")
            .putData("notificationId", requireNotNull(saved.id).toString())
            .putData("reportId", reportId.toString())
            .putData("status", status)
            .putData("title", title)
            .putData("body", body.take(600))
            .build()
        val send = {
            try {
                FirebaseMessaging.getInstance().send(message)
            } catch (e: Exception) {
                logger.error("Gửi FCM báo cáo thất bại cho user $recipientId, reportId=$reportId", e)
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() { send() }
            })
        } else send()
    }

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

        val message = Message.builder()
            .setToken(fcmToken)
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build()
            )
            .putData("recipientId", requireNotNull(recipient.id))
            .putData("title", senderName)
            .putData("body", messageText)
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
        val savedNotification = inbox.record(requireNotNull(recipient.id), orderId, status, title, body)
        val fcmToken = recipient.fcmToken
        if (fcmToken.isNullOrBlank()) {
            logger.warn("Không gửi thông báo đơn hàng: user ${recipient.id} chưa có fcmToken")
            return
        }

        val message = Message.builder()
            .setToken(fcmToken)
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build()
            )
            .putData("recipientId", requireNotNull(recipient.id))
            .putData("type", "ORDER_STATUS")
            .putData("notificationId", requireNotNull(savedNotification.id).toString())
            .putData("orderId", orderId.toString())
            .putData("status", status)
            .putData("title", title)
            .putData("body", body)
            .build()

        val send = {
            try {
                FirebaseMessaging.getInstance().send(message)
                logger.info("Đã gửi FCM thông báo đơn hàng #$orderId (status=$status) tới user ${recipient.id}")
            } catch (e: Exception) {
                logger.error("Gửi FCM đơn hàng thất bại cho user ${recipient.id}, orderId=$orderId", e)
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() { send() }
            })
        } else send()
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
