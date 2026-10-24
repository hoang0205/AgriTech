package com.uet.agritech.notification

import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.springframework.data.domain.PageImpl

class NotificationInboxServiceTest {
    private val inbox = mockk<NotificationInboxRepository>()
    private val users = mockk<UserRepository>()
    private val user = mockk<User>()
    private val service = NotificationInboxService(inbox, users)

    init {
        every { user.id } returns "u1"
        every { users.findByPhoneNumber("phone") } returns Optional.of(user)
    }

    @Test
    fun `report notification is saved without push token and routes to reports`() {
        val records = mockk<NotificationInboxService>()
        every { user.fcmToken } returns null
        every { users.findById("u1") } returns Optional.of(user)
        val saved = UserNotification(id = 8, recipientId = "u1", reportId = 7,
            type = "REPORT_STATUS", status = "RESOLVED", title = "Title", body = "Body")
        every { records.recordReport("u1", 7, "RESOLVED", any(), any()) } returns saved
        NotificationService(users, records).sendReportStatusNotification("u1", 7, "RESOLVED", "Rau", "Đã xử lý")
        verify { records.recordReport("u1", 7, "RESOLVED", any(), match { it.contains("Đã xử lý") }) }

        every { inbox.findAllByRecipientIdOrderByCreatedAtDescIdDesc("u1", any()) } returns PageImpl(listOf(saved))
        every { inbox.countByRecipientIdAndReadAtIsNull("u1") } returns 1
        val item = service.list("phone", 0, 20).items.single()
        assertEquals("my_reports", item.targetScreen)
        assertEquals(7L, item.reportId)
        assertEquals(null, item.orderId)
        assertEquals("REPORT_STATUS", item.type)
    }

    @Test
    fun `users cannot mark another user's notification read`() {
        val item = UserNotification(id = 1, recipientId = "other", orderId = 10, status = "PENDING", title = "Title", body = "Body")
        every { inbox.findById(1) } returns Optional.of(item)
        val error = assertFailsWith<ResponseStatusException> { service.read("phone", 1) }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        verify(exactly = 0) { inbox.saveAndFlush(any()) }
    }

    @Test
    fun `reading a notification again preserves its original read time`() {
        val readAt = Instant.parse("2026-10-20T00:00:00Z")
        val item = UserNotification(id = 1, recipientId = "u1", orderId = 10, status = "PENDING", title = "Title", body = "Body", readAt = readAt)
        every { inbox.findById(1) } returns Optional.of(item)
        every { inbox.countByRecipientIdAndReadAtIsNull("u1") } returns 2
        assertEquals(2L, service.read("phone", 1).count)
        assertEquals(readAt, item.readAt)
        verify(exactly = 0) { inbox.saveAndFlush(any()) }
    }

    @Test
    fun `mark all uses the displayed cutoff and preserves newer unread notifications`() {
        every { inbox.markAllRead("u1", 100, any()) } returns 5
        every { inbox.countByRecipientIdAndReadAtIsNull("u1") } returns 1
        assertEquals(1L, service.readAll("phone", 100).count)
        verify(exactly = 1) { inbox.markAllRead("u1", 100, any()) }
    }

    @Test
    fun `order notification is stored even when user has no push token`() {
        val records = mockk<NotificationInboxService>()
        every { user.fcmToken } returns null
        val saved = UserNotification(id = 1, recipientId = "u1", orderId = 10, status = "PENDING", title = "Title", body = "Body")
        every { records.record("u1", 10, "PENDING", "Title", "Body") } returns saved
        NotificationService(users, records).sendOrderStatusNotification(user, 10, "PENDING", "Title", "Body")
        verify(exactly = 1) { records.record("u1", 10, "PENDING", "Title", "Body") }
        assertNotNull(saved.id)
    }
}
