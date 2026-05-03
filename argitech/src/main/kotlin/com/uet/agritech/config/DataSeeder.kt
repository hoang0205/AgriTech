package com.uet.agritech.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.uet.agritech.config.province.Province
import com.uet.agritech.config.province.ProvinceRepository
import com.uet.agritech.config.province.Ward
import com.uet.agritech.config.province.WardRepository
import com.uet.agritech.product.Product
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DataSeeder(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val passwordEncoder: PasswordEncoder,
    private val provinceRepository: ProvinceRepository,
    private val wardRepository: WardRepository,
    private val objectMapper: ObjectMapper
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String) {
        if (provinceRepository.count() == 0L) {
            println("🚚 DEBUG: Đang nạp dữ liệu hành chính mới...")
            try {
                val provinceMap = loadMapData("data/province.json")
                val provinces = provinceMap.values.map {
                    Province(code = it.code, name = it.name, type = it.type)
                }
                provinceRepository.saveAll(provinces)

                val wardMap = loadMapData("data/ward.json")
                val wards = wardMap.values.mapNotNull { dto ->
                    val province = provinceRepository.findById(dto.parent_code ?: "").orElse(null)
                    if (province != null) {
                        Ward(code = dto.code, name = dto.name, type = dto.type, province = province)
                    } else null
                }
                wardRepository.saveAll(wards)
                println("✅ DEBUG: Nạp tỉnh thành và xã phường thành công!")
            } catch (e: Exception) {
                println("❌ ERROR: Lỗi khi nạp dữ liệu hành chính: ${e.message}")
            }
        }

        val savedUsers = mutableListOf<User>()
        if (userRepository.count() == 0L) {
            println("🧑‍🌾 DEBUG: Đang tạo dữ liệu User mẫu...")
            val sampleUsers = listOf(
                User(
                    fullName = "Nguyễn Khánh Ly",
                    phoneNumber = "0363632030",
                    email = "khanhly2507@gmail.com",
                    password = passwordEncoder.encode("02052005Hoang").toString(),
                    role = Role.FARMER
                ),
                User(
                    fullName = "Đoàn Văn Hoàng",
                    phoneNumber = "0835817188",
                    email = "hoangdv@gmail.com",
                    password = passwordEncoder.encode("123456").toString(),
                    role = Role.FARMER
                ),
                User(
                    fullName = "Nông Dân Ba Tri",
                    phoneNumber = "0987654321",
                    email = "nongdanbato@gmail.com",
                    password = passwordEncoder.encode("123456").toString(),
                    role = Role.FARMER
                ),
                User(
                    fullName = "Khách Hàng Vip",
                    phoneNumber = "0555555555",
                    email = "buyer_vip@gmail.com",
                    password = passwordEncoder.encode("123456").toString(),
                    role = Role.BUYER
                ),
                User(
                    fullName = "Admin Hệ Thống",
                    phoneNumber = "0999999999",
                    email = "admin@agritech.com",
                    password = passwordEncoder.encode("admin123").toString(),
                    role = Role.ADMIN
                )
            )
            savedUsers.addAll(userRepository.saveAll(sampleUsers))
            println("✅ DEBUG: Đã tạo xong ${savedUsers.size} User mẫu!")
        } else {
            savedUsers.addAll(userRepository.findAll())
            println("ℹ️ DEBUG: Đã có User, bỏ qua nạp User mẫu.")
        }

        val farmers = savedUsers.filter { it.role == Role.FARMER }

        if (productRepository.count() == 0L && farmers.isNotEmpty()) {
            println("🍎 DEBUG: Đang tạo 15 sản phẩm mẫu...")

            val mockProducts = listOf(
                Product(name = "Cà chua Cherry Đà Lạt", category = "Rau củ", price = 45000.0, quantity = 100.0, unit = "kg", description = "Cà chua siêu ngọt, trồng nhà màng.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1596199010100-db2b10084b63?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Dưa hấu Long An", category = "Trái cây", price = 20000.0, quantity = 200.0, unit = "kg", description = "Vỏ mỏng, đỏ au, ngọt lịm tim.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1589984662646-e7b2e4962f18?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Khoai lang mật", category = "Củ quả", price = 35000.0, quantity = 50.0, unit = "kg", description = "Khoai lang nướng chảy mật cực thơm.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1596040033229-a9821ebd058d?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Xoài Cát Hòa Lộc", category = "Trái cây", price = 65000.0, quantity = 30.0, unit = "kg", description = "Đặc sản trái cây miền Tây, chín cây tự nhiên.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1553284965-83fd3e82fa5a?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Gạo ST25 Hữu cơ", category = "Ngũ cốc", price = 40000.0, quantity = 500.0, unit = "kg", description = "Gạo ngon nhất thế giới, trồng chuẩn organic.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1586201375761-83865001e31c?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Sầu riêng Ri6", category = "Trái cây", price = 120000.0, quantity = 20.0, unit = "kg", description = "Cơm vàng hạt lép, bao ăn từng múi.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1621317581561-249e917d2a58?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Rau muống tiến vua", category = "Rau củ", price = 15000.0, quantity = 80.0, unit = "bó", description = "Rau non mơn mởn, hái lúc sáng sớm.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1601275868399-45be0380ad91?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Mật ong rừng ngập mặn", category = "Đặc sản", price = 250000.0, quantity = 10.0, unit = "lít", description = "Mật ong nguyên chất 100%, vắt thủ công.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1587049352847-4d4b1ed7d889?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Cam sành Vĩnh Long", category = "Trái cây", price = 25000.0, quantity = 150.0, unit = "kg", description = "Nhiều nước, chua ngọt thanh mát.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1611080626919-7cf5a9dbab5b?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Nấm rơm hữu cơ", category = "Nấm", price = 80000.0, quantity = 25.0, unit = "kg", description = "Nấm sạch trồng nhà kín, giòn ngọt.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1590634626155-fc42eb9bc2f6?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Thanh long ruột đỏ", category = "Trái cây", price = 30000.0, quantity = 120.0, unit = "kg", description = "Đẹp mắt, giàu vitamin C.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1594282486552-05b4d79fdd9f?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Hành tây Đà Lạt", category = "Rau củ", price = 18000.0, quantity = 300.0, unit = "kg", description = "Hành củ to, giòn, không hăng.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1618512496248-a07fe83aa8cb?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Tỏi cô đơn Lý Sơn", category = "Gia vị", price = 850000.0, quantity = 5.0, unit = "kg", description = "Loại 1 củ duy nhất, cực kỳ quý hiếm.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1586555191845-d8641199320d?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Bưởi da xanh Bến Tre", category = "Trái cây", price = 75000.0, quantity = 40.0, unit = "quả", description = "Ruột hồng, không hạt, chua ngọt tự nhiên.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1574856344991-aaa31b6f4ce3?q=80&w=800"), farmer = farmers.random()),
                Product(name = "Cà rốt Baby", category = "Rau củ", price = 50000.0, quantity = 60.0, unit = "kg", description = "Rất tốt cho bé ăn dặm, vị ngọt thanh.", imageUrls = mutableListOf("https://images.unsplash.com/photo-1598170845058-32b9d6a5da37?q=80&w=800"), farmer = farmers.random())
            )

            productRepository.saveAll(mockProducts)
            println("✅ DEBUG: Đã tạo xong 15 sản phẩm test và gán ngẫu nhiên cho các Nông Dân!")
        } else {
            println("ℹ️ DEBUG: Đã có sản phẩm hoặc không có Nông dân nào, bỏ qua nạp sản phẩm mẫu.")
        }
    }

    private fun loadMapData(path: String): Map<String, AddressUnitDTO> {
        val resource = ClassPathResource(path)
        val typeRef = object : TypeReference<Map<String, AddressUnitDTO>>() {}
        return objectMapper.readValue(resource.inputStream, typeRef)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AddressUnitDTO(
    val name: String,
    val type: String,
    val code: String,
    val parent_code: String? = null
)

@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().registerKotlinModule()
    }
}