package com.uet.agritech.review

import com.uet.agritech.product.ProductRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductSummaryWriter(
    private val productRepository: ProductRepository,
    private val entityManager: EntityManager
) {
    @Transactional
    fun update(
        productId: String,
        pros: String,
        cons: String
    ) {
        val product = productRepository.findForStockUpdate(productId)
            .orElseThrow {
                RuntimeException("Sản phẩm không tồn tại: $productId")
            }

        entityManager.refresh(
            product,
            LockModeType.PESSIMISTIC_WRITE
        )

        product.aiPros = pros
        product.aiCons = cons
    }
}