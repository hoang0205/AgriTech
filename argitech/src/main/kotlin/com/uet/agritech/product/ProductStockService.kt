package com.uet.agritech.product

import com.uet.agritech.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

data class UpdateProductStockRequest(val quantity: Double? = null)
data class ProductStockResponse(val productId: String, val quantity: Double)

@Service
class ProductStockService(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) {
    @Transactional
    fun updateStock(phone: String, productId: String, request: UpdateProductStockRequest): ProductStockResponse {
        val quantity = request.quantity
        if (quantity == null || !quantity.isFinite() || quantity < 0.0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Tồn kho phải là số hợp lệ và không được âm")
        }
        val seller = userRepository.findByPhoneNumber(phone).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập")
        }
        val product = productRepository.findForStockUpdate(productId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Sản phẩm không tồn tại")
        }
        if (product.farmer.id != seller.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn chỉ được sửa tồn kho sản phẩm của mình")
        }
        product.quantity = quantity
        productRepository.save(product)
        return ProductStockResponse(requireNotNull(product.id), product.quantity)
    }
}
