package com.uet.agritech.config // Sửa lại package cho đúng với project của ông

import com.uet.agritech.product.Product
import com.uet.agritech.product.ProductRepository
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class DataSeeder(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String) {
        if (productRepository.count() > 0) {
            println("DEBUG: DB đã có sản phẩm, bỏ qua Seeder.")
            return
        }

        println("DEBUG: Đang tạo 15 sản phẩm (Mỗi sản phẩm có 2-4 ảnh) để test vuốt ngang...")

        // 1. Khởi tạo 4 tài khoản Nông dân
        val phoneNumbers = listOf("0835817188", "0363632030", "123456789", "05555555555")
        val farmers = mutableListOf<User>()

        for (phone in phoneNumbers) {
            val existingUser = userRepository.findByPhoneNumber(phone)
            if (existingUser.isEmpty) {
                val newUser = User(
                    fullName = "Nông dân $phone",
                    phoneNumber = phone,
                    email = "farmer_$phone@gmail.com",
                    password = passwordEncoder.encode("123456").toString(),
                    role = Role.FARMER
                )
                farmers.add(userRepository.save(newUser))
            } else {
                farmers.add(existingUser.get())
            }
        }

        // 2. Tạo sẵn các rổ ảnh (Mỗi rổ 2-4 ảnh khác biệt)
        val imgTomatoes = mutableListOf(
            "https://images.unsplash.com/photo-1596199010100-db2b10084b63?q=80&w=800",
            "https://images.unsplash.com/photo-1518977822534-7049a61fe022?q=80&w=800",
            "https://images.unsplash.com/photo-1558818498-28c1e002b655?q=80&w=800"
        )
        val imgWatermelons = mutableListOf(
            "https://images.unsplash.com/photo-1589984662646-e7b2e4962f18?q=80&w=800",
            "https://images.unsplash.com/photo-1618897996318-c46e140db453?q=80&w=800",
            "https://images.unsplash.com/photo-1563114773-84221bd62daa?q=80&w=800"
        )
        val imgCabbages = mutableListOf(
            "https://images.unsplash.com/photo-1540420773420-3366772f5159?q=80&w=800",
            "https://images.unsplash.com/photo-1599824636916-2586e245cdce?q=80&w=800"
        )
        val imgDurians = mutableListOf(
            "https://images.unsplash.com/photo-1601053745233-1e582e0617be?q=80&w=800",
            "https://images.unsplash.com/photo-1643195200230-05e8392119c6?q=80&w=800"
        )
        val imgMeats = mutableListOf(
            "https://images.unsplash.com/photo-1606854426297-f0b8d5a7114b?q=80&w=800",
            "https://images.unsplash.com/photo-1602499699661-827fb714856f?q=80&w=800",
            "https://images.unsplash.com/photo-1587595431973-160d0d94add1?q=80&w=800"
        )
        val imgApples = mutableListOf(
            "https://images.unsplash.com/photo-1560806887-1e4cd0b6faa6?q=80&w=800",
            "https://images.unsplash.com/photo-1619546813926-a78fa6372cd2?q=80&w=800"
        )
        val imgHoney = mutableListOf(
            "https://images.unsplash.com/photo-1587049352846-4a222e784d38?q=80&w=800",
            "https://images.unsplash.com/photo-1621625902047-814d42042854?q=80&w=800",
            "https://images.unsplash.com/photo-1554522723-b2a47cb105e3?q=80&w=800"
        )
        val imgCoffee = mutableListOf(
            "https://images.unsplash.com/photo-1595434091143-b375ace5d46d?q=80&w=800",
            "https://images.unsplash.com/photo-1559525839-b184a4d698c7?q=80&w=800",
            "https://images.unsplash.com/photo-1587734195503-904fca47e0e9?q=80&w=800"
        )

        // 3. Đổ 15 sản phẩm vào DB, random Nông dân
        val mockProducts = listOf(
            Product(name = "Cà chua Cherry Đà Lạt", category = "Rau củ", price = 45000.0, quantity = 100.0, unit = "kg", description = "Cà chua siêu ngọt, trồng nhà màng.", imageUrls = imgTomatoes.toMutableList(), farmer = farmers.random()),
            Product(name = "Cà chua Beef size đại", category = "Rau củ", price = 35000.0, quantity = 80.0, unit = "kg", description = "Thích hợp làm salad, kẹp hamburger.", imageUrls = imgTomatoes.toMutableList(), farmer = farmers.random()),
            Product(name = "Dưa hấu Long An", category = "Trái cây", price = 20000.0, quantity = 200.0, unit = "kg", description = "Vỏ mỏng, đỏ au, ngọt lịm tim.", imageUrls = imgWatermelons.toMutableList(), farmer = farmers.random()),
            Product(name = "Dưa hấu không hạt", category = "Trái cây", price = 25000.0, quantity = 150.0, unit = "kg", description = "Ăn mát lạnh, không lo nhằn hạt.", imageUrls = imgWatermelons.toMutableList(), farmer = farmers.random()),
            Product(name = "Bắp cải trái tim", category = "Rau củ", price = 25000.0, quantity = 50.0, unit = "kg", description = "Ngọt giòn, luộc xào đều cực phẩm.", imageUrls = imgCabbages.toMutableList(), farmer = farmers.random()),
            Product(name = "Bắp cải tím Đà Lạt", category = "Rau củ", price = 30000.0, quantity = 40.0, unit = "kg", description = "Làm salad detox thì hết sẩy.", imageUrls = imgCabbages.toMutableList(), farmer = farmers.random()),
            Product(name = "Sầu riêng Ri6 Hạt Lép", category = "Trái cây", price = 120000.0, quantity = 30.0, unit = "kg", description = "Cơm vàng óng, bao ăn 1 đổi 1.", imageUrls = imgDurians.toMutableList(), farmer = farmers.random()),
            Product(name = "Sầu riêng Musang King", category = "Trái cây", price = 350000.0, quantity = 10.0, unit = "kg", description = "Hàng tuyển chọn, đẳng cấp Vua sầu.", imageUrls = imgDurians.toMutableList(), farmer = farmers.random()),
            Product(name = "Thịt heo thảo mộc", category = "Thịt", price = 150000.0, quantity = 80.0, unit = "kg", description = "Heo nuôi bằng thảo mộc, mỡ giòn.", imageUrls = imgMeats.toMutableList(), farmer = farmers.random()),
            Product(name = "Ba chỉ bò Úc thả đồng", category = "Thịt", price = 220000.0, quantity = 60.0, unit = "kg", description = "Thịt xen mỡ cực mềm, nhúng lẩu bao phê.", imageUrls = imgMeats.toMutableList(), farmer = farmers.random()),
            Product(name = "Táo Fuji Nhật", category = "Trái cây", price = 90000.0, quantity = 100.0, unit = "kg", description = "Táo nhập khẩu giòn tan, mọng nước.", imageUrls = imgApples.toMutableList(), farmer = farmers.random()),
            Product(name = "Táo xanh Ninh Thuận", category = "Trái cây", price = 40000.0, quantity = 120.0, unit = "kg", description = "Vị chua ngọt tự nhiên, chấm muối ớt bao dính.", imageUrls = imgApples.toMutableList(), farmer = farmers.random()),
            Product(name = "Mật ong rừng nguyên tổ", category = "Đặc sản", price = 350000.0, quantity = 20.0, unit = "lít", description = "Có sẵn tổ ong nguyên sáp bên trong.", imageUrls = imgHoney.toMutableList(), farmer = farmers.random()),
            Product(name = "Mật ong hoa cafe", category = "Đặc sản", price = 200000.0, quantity = 50.0, unit = "lít", description = "Thơm mùi hoa cafe Tây Nguyên.", imageUrls = imgHoney.toMutableList(), farmer = farmers.random()),
            Product(name = "Cà phê Robusta rang mộc", category = "Đồ uống", price = 180000.0, quantity = 60.0, unit = "kg", description = "Cà phê nguyên chất, hậu vị đậm đà.", imageUrls = imgCoffee.toMutableList(), farmer = farmers.random())
        )

        productRepository.saveAll(mockProducts)
        println("DEBUG: Thành công rực rỡ! Đã tạo ${productRepository.count()} sản phẩm, mỗi cái có 1 rổ ảnh.")
    }
}