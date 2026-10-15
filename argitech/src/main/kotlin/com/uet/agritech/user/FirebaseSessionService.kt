package com.uet.agritech.user

import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.cloud.FirestoreClient
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class FirebaseSessionService {

    fun createLoginToken(user: User): String {
        try {
            val uid = requireNotNull(user.id)

            saveSessionVersion(uid, user.tokenVersion)

            return FirebaseAuth.getInstance().createCustomToken(
                uid,
                mapOf<String, Any>(
                    "sessionVersion" to user.tokenVersion
                )
            )
        } catch (e: Exception) {
            throwAsRuntime(e)
        }
    }

    fun revokeSessions(user: User) {
        try {
            val uid = requireNotNull(user.id)

            saveSessionVersion(uid, user.tokenVersion)

            try {
                FirebaseAuth.getInstance().revokeRefreshTokens(uid)
            } catch (e: FirebaseAuthException) {
                if (e.authErrorCode != AuthErrorCode.USER_NOT_FOUND) {
                    throw e
                }
            }
        } catch (e: Exception) {
            throwAsRuntime(e)
        }
    }

    private fun saveSessionVersion(uid: String, version: Long) {
        FirestoreClient.getFirestore()
            .collection("auth_sessions")
            .document(uid)
            .set(
                mapOf<String, Any>(
                    "sessionVersion" to version
                )
            )
            .get(10, TimeUnit.SECONDS)
    }

    private fun throwAsRuntime(e: Exception): Nothing {
        if (e is InterruptedException) {
            Thread.currentThread().interrupt()
        }

        throw IllegalStateException(
            "Không cập nhật được phiên Firebase",
            e
        )
    }
}