package com.uet.agritech.notification

import com.google.cloud.firestore.SetOptions
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.uet.agritech.bargain.BargainOffer
import com.uet.agritech.user.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
class BargainNotifications {
    private val logger = LoggerFactory.getLogger(javaClass)
    fun send(offer: BargainOffer, recipient: User, saved: UserNotification, title: String) {
        // Copy data before commit; Firebase failures must not undo an accepted offer.
        val buyerId = requireNotNull(offer.buyer.id)
        val sellerId = requireNotNull(offer.seller.id)
        val roomId = listOf(buyerId, sellerId).sorted().joinToString("_")
        val actor = if (recipient.id == buyerId) offer.seller else offer.buyer
        val eventId = "bargain_${offer.id}_${offer.status}"
        val message = mapOf("id" to eventId, "senderId" to requireNotNull(actor.id), "senderName" to actor.fullName,
            "text" to "$title\n${saved.body}\nMở đề nghị để xem trạng thái mới nhất.",
            "timestamp" to System.currentTimeMillis(), "bargainId" to requireNotNull(offer.id))
        val metadata = mapOf("roomId" to roomId, "participants" to listOf(buyerId, sellerId),
            "lastMessage" to title, "lastUpdated" to System.currentTimeMillis(),
            "partnerName_$buyerId" to offer.buyer.fullName, "partnerName_$sellerId" to offer.seller.fullName)
        val token = recipient.fcmToken
        val recipientId = requireNotNull(recipient.id)
        val afterCommit = {
            runCatching {
                val db = FirestoreClient.getFirestore()
                val room = db.collection("chats").document(roomId)
                db.batch().set(room, metadata, SetOptions.merge())
                    .set(room.collection("messages").document(eventId), message).commit().get(5, java.util.concurrent.TimeUnit.SECONDS)
            }.onFailure { logger.warn("Không đồng bộ thẻ trả giá {} vào chat", eventId, it) }
            if (!token.isNullOrBlank()) runCatching {
                FirebaseMessaging.getInstance().send(Message.builder().setToken(token)
                    .putData("recipientId", recipientId).putData("type", "BARGAIN")
                    .putData("notificationId", saved.id.toString())
                    .putData("title", title).putData("body", saved.body)
                    .putData("targetScreen", "bargains").build())
            }.onFailure { logger.warn("Không gửi được thông báo trả giá {}", eventId, it) }
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() { afterCommit() }
        })
    }
}
