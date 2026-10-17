package com.uet.agritech.product

import com.uet.agritech.product.dto.ProductCostResponse
import com.uet.agritech.product.dto.UpdateProductCostRequest
import com.uet.agritech.user.Role
import com.uet.agritech.user.User
import com.uet.agritech.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ProductCostService(
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository
) {

    @Transactional(readOnly = true)
    fun getMyProductCosts(
        sellerPhone: String
    ): List<ProductCostResponse> {
        val seller = requireSeller(sellerPhone)

        return productRepository
            .findAllByFarmerOrderByNameAsc(seller)
            .map { it.toCostResponse() }
    }

    @Transactional
    fun updateCost(
        sellerPhone: String,
        productId: String,
        request: UpdateProductCostRequest
    ): ProductCostResponse {
        val seller = requireSeller(sellerPhone)

        val costPrice = request.costPrice
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Vui lòng nhập giá vốn"
            )

        if (!costPrice.isFinite() || costPrice < 0.0) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Giá vốn phải là số hợp lệ và không được âm"
            )
        }

        val product = productRepository
            .findForStockUpdate(productId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Sản phẩm không tồn tại"
                )
            }

        if (product.farmer.id != seller.id) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Bạn chỉ được sửa giá vốn sản phẩm của mình"
            )
        }

        product.costPrice = costPrice

        return productRepository
            .save(product)
            .toCostResponse()
    }

    private fun requireSeller(phone: String): User {
        val seller = userRepository
            .findByPhoneNumber(phone)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tài khoản không tồn tại"
                )
            }
        return seller
    }

    private fun Product.toCostResponse(): ProductCostResponse {
        return ProductCostResponse(
            productId = requireNotNull(id),
            productName = name,
            unit = unit,
            sellingPrice = price,
            costPrice = costPrice,
            quantity = quantity
        )
    }
}