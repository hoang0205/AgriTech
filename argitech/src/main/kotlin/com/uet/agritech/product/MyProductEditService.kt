package com.uet.agritech.product

import com.uet.agritech.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class EditableProductResponse(
    val id: String,
    val name: String,
    val category: String,
    val price: Double,
    val quantity: Double,
    val unit: String,
    val description: String,
    val imageUrls: List<String>,
    val costPrice: Double?
)

data class UpdateMyProductRequest(
    val name: String,
    val category: String,
    val price: Double,
    val unit: String,
    val description: String,
    val imageUrls: List<String>,
    val costPrice: Double? = null,
    val quantity: Double? = null,
    val expectedQuantity: Double? = null
)

@Service
class MyProductEditService(
    private val products: ProductRepository,
    private val users: UserRepository
) {
    @Transactional(readOnly = true)
    fun getProduct(phone: String, productId: String): EditableProductResponse {
        val product = products.findById(productId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Sản phẩm không tồn tại")
        }
        requireOwner(phone, product)
        return product.toEditableResponse()
    }

    @Transactional
    fun updateProduct(phone: String, productId: String, request: UpdateMyProductRequest): EditableProductResponse {
        val product = products.findForStockUpdate(productId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Sản phẩm không tồn tại")
        }
        requireOwner(phone, product)
        val name = request.name.trim()
        val unit = request.unit.trim()
        val category = request.category.trim()
        require(name.isNotBlank() && name.length <= 255) { "Tên sản phẩm phải có từ 1 đến 255 ký tự" }
        require(unit.isNotBlank() && unit.length <= 255) { "Đơn vị tính phải có từ 1 đến 255 ký tự" }
        require(category in setOf("Rau củ", "Trái cây", "Thịt", "Thủy hải sản", "Sản phẩm khác",
            "Vegetables", "Fruits", "Meat", "Seafood", "Others")) { "Danh mục sản phẩm không hợp lệ" }
        require(request.price.isFinite() && request.price > 0.0) { "Giá bán phải là số lớn hơn 0" }
        require(request.costPrice == null || (request.costPrice.isFinite() && request.costPrice >= 0.0)) {
            "Giá vốn phải là số không âm"
        }
        require(request.description.length <= 10000) { "Mô tả không được vượt quá 10.000 ký tự" }
        require(request.imageUrls.size in 1..5 && request.imageUrls.all { it.isNotBlank() && it.length <= 2048 }) {
            "Vui lòng chọn từ 1 đến 5 ảnh sản phẩm hợp lệ"
        }
        request.quantity?.let { quantity ->
            require(quantity.isFinite() && quantity >= 0.0) { "Tồn kho phải là số không âm" }
            require(request.expectedQuantity != null && request.expectedQuantity.isFinite() && request.expectedQuantity >= 0.0) {
                "Thiếu thông tin tồn kho ban đầu. Vui lòng tải lại sản phẩm"
            }
            if (product.quantity != request.expectedQuantity) {
                throw ResponseStatusException(HttpStatus.CONFLICT,
                    "Tồn kho đã thay đổi trong lúc bạn chỉnh sửa. Vui lòng tải lại sản phẩm trước khi lưu.")
            }
            product.quantity = quantity
        }
        product.name = name
        product.category = category
        product.price = request.price
        product.unit = unit
        product.description = request.description.trim()
        product.costPrice = request.costPrice
        product.imageUrls.clear()
        product.imageUrls.addAll(request.imageUrls.map { it.trim() })
        return products.save(product).toEditableResponse()
    }

    private fun requireOwner(phone: String, product: Product) {
        val seller = users.findByPhoneNumber(phone).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập")
        }
        if (product.farmer.id != seller.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn chỉ được sửa sản phẩm của mình")
        }
    }

    private fun Product.toEditableResponse() = EditableProductResponse(
        id = requireNotNull(id), name = name, category = category, price = price,
        quantity = quantity, unit = unit, description = description,
        imageUrls = imageUrls.toList(), costPrice = costPrice
    )
}
