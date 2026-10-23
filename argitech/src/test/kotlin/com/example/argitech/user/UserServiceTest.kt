package com.uet.agritech.user

import com.uet.agritech.security.JwtService
import com.uet.agritech.user.dto.ChangePasswordRequest
import com.uet.agritech.user.dto.UpdateProfileRequest
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional
import kotlin.test.*

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var service: UserService
    private lateinit var user: User

    private val passwordEncoder = BCryptPasswordEncoder(4)

    private val phone = "0900000001"
    private val oldPassword = "Old@123456"
    private val oldAvatar = "https://example.test/old.jpg"

    @BeforeEach
    fun setUp() {
        userRepository = mockk()

        user = User(
            id = "user-1",
            fullName = "Old name",
            phoneNumber = phone,
            email = "user@example.test",
            password = passwordEncoder.encode(oldPassword).toString(),
            role = Role.BUYER,
            avatarUrl = oldAvatar
        )

        every {
            userRepository.findByPhoneNumber(phone)
        } returns Optional.of(user)

        every {
            userRepository.save(any<User>())
        } answers {
            firstArg<User>()
        }

        service = UserService(
            userRepository,
            mockk<JwtService>(),
            passwordEncoder,
            mockk<jakarta.persistence.EntityManager>(relaxUnitFun = true),
            mockk<FirebaseSessionService>(relaxUnitFun = true)
        )
    }

    @Test
    fun `cap nhat ten va avatar thanh cong`() {
        val newAvatar = "https://example.test/new.jpg"

        val result = service.updateProfile(
            phone,
            UpdateProfileRequest(
                fullName = "New name",
                avatarUrl = newAvatar
            )
        )

        // Kiểm tra dữ liệu trả về.
        assertEquals("New name", result.fullName)
        assertEquals(newAvatar, result.avatarUrl)

        // Kiểm tra dữ liệu được lưu.
        assertEquals("New name", user.fullName)
        assertEquals(newAvatar, user.avatarUrl)

        verify(exactly = 1) {
            userRepository.save(user)
        }
    }

    @Test
    fun `chi doi ten thi giu nguyen avatar`() {
        service.updateProfile(
            phone,
            UpdateProfileRequest(
                fullName = "New name",
                avatarUrl = null
            )
        )

        assertEquals("New name", user.fullName)
        assertEquals(oldAvatar, user.avatarUrl)

        verify(exactly = 1) {
            userRepository.save(user)
        }
    }

    @Test
    fun `chi doi avatar thi giu nguyen ten`() {
        val newAvatar = "https://example.test/new.jpg"

        service.updateProfile(
            phone,
            UpdateProfileRequest(
                fullName = null,
                avatarUrl = newAvatar
            )
        )

        assertEquals("Old name", user.fullName)
        assertEquals(newAvatar, user.avatarUrl)

        verify(exactly = 1) {
            userRepository.save(user)
        }
    }

    @Test
    fun `nguoi dung khong ton tai thi khong cap nhat ho so`() {
        every {
            userRepository.findByPhoneNumber(phone)
        } returns Optional.empty()

        assertFailsWith<RuntimeException> {
            service.updateProfile(
                phone,
                UpdateProfileRequest(
                    fullName = "New name",
                    avatarUrl = null
                )
            )
        }

        verify(exactly = 0) {
            userRepository.save(any<User>())
        }
    }

    @Test
    fun `doi mat khau thanh cong va luu dang bam`() {
        val newPassword = "New@123456"

        service.changePassword(
            ChangePasswordRequest(
                oldPassword = oldPassword,
                newPassword = newPassword,
                confirmPassword = newPassword
            ),
            phone
        )

        // Mật khẩu mới phải khớp với hash đã lưu.
        assertTrue(
            passwordEncoder.matches(newPassword, user.password)
        )

        // Mật khẩu cũ không còn khớp.
        assertFalse(
            passwordEncoder.matches(oldPassword, user.password)
        )

        // Không được lưu mật khẩu dạng văn bản thuần.
        assertNotEquals(newPassword, user.password)

        verify(exactly = 1) {
            userRepository.save(user)
        }
    }

    @Test
    fun `mat khau cu sai thi khong thay doi mat khau`() {
        assertPasswordRejected(
            ChangePasswordRequest(
                oldPassword = "Wrong@123456",
                newPassword = "New@123456",
                confirmPassword = "New@123456"
            )
        )
    }

    @Test
    fun `xac nhan khong khop thi khong thay doi mat khau`() {
        assertPasswordRejected(
            ChangePasswordRequest(
                oldPassword = oldPassword,
                newPassword = "New@123456",
                confirmPassword = "Different@123456"
            )
        )
    }

    @Test
    fun `mat khau moi trung mat khau cu thi bi tu choi`() {
        assertPasswordRejected(
            ChangePasswordRequest(
                oldPassword = oldPassword,
                newPassword = oldPassword,
                confirmPassword = oldPassword
            )
        )
    }

    private fun assertPasswordRejected(
        request: ChangePasswordRequest
    ) {
        val storedPasswordBefore = user.password

        assertFailsWith<RuntimeException> {
            service.changePassword(request, phone)
        }

        // Yêu cầu sai không được làm thay đổi hash hiện tại.
        assertEquals(storedPasswordBefore, user.password)

        verify(exactly = 0) {
            userRepository.save(any<User>())
        }
    }
}
