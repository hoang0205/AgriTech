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
                println("✅ DEBUG: Nạp 34 tỉnh thành và xã phường thành công!")
            } catch (e: Exception) {
                println("❌ ERROR: Lỗi khi nạp dữ liệu hành chính: ${e.message}")
            }
        }

        if (productRepository.count() == 0L) {
            println("🍎 DEBUG: Đang tạo 15 sản phẩm mẫu...")

            val phoneNumbers = listOf("0835817188", "0363632030", "123456789", "05555555555")
            val farmers = phoneNumbers.map { phone ->
                userRepository.findByPhoneNumber(phone).orElseGet {
                    userRepository.save(User(
                        fullName = "Nông dân $phone",
                        phoneNumber = phone,
                        email = "farmer_$phone@gmail.com",
                        password = passwordEncoder.encode("123456").toString(),
                        role = Role.FARMER
                    ))
                }
            }

            val imgTomatoes = mutableListOf("https://images.unsplash.com/photo-1596199010100-db2b10084b63?q=80&w=800", "https://images.unsplash.com/photo-1518977822534-7049a61fe022?q=80&w=800")
            val imgWatermelons = mutableListOf("https://images.unsplash.com/photo-1589984662646-e7b2e4962f18?q=80&w=800", "https://images.unsplash.com/photo-1563114773-84221bd62daa?q=80&w=800")

            val mockProducts = listOf(
                Product(name = "Cà chua Cherry Đà Lạt", category = "Rau củ", price = 45000.0, quantity = 100.0, unit = "kg", description = "Cà chua siêu ngọt, trồng nhà màng.", imageUrls = imgTomatoes.toMutableList(), farmer = farmers.random()),
                Product(name = "Dưa hấu Long An", category = "Trái cây", price = 20000.0, quantity = 200.0, unit = "kg", description = "Vỏ mỏng, đỏ au, ngọt lịm tim.", imageUrls = imgWatermelons.toMutableList(), farmer = farmers.random())
            )

            productRepository.saveAll(mockProducts)
            println("✅ DEBUG: Đã tạo xong 15 sản phẩm test!")
        } else {
            println("ℹ️ DEBUG: Đã có sản phẩm, bỏ qua nạp sản phẩm mẫu.")
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
