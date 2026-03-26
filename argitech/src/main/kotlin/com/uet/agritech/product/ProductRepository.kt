package com.uet.agritech.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ProductRepository : JpaRepository<Product, String> {

    fun findByFarmerId(farmerId: String): List<Product>

    fun findByCategory(category: String, pageable: Pageable): Page<Product>

    fun findByNameContainingIgnoreCase(name: String): List<Product>

    @Query(value = "SELECT * FROM products ORDER BY RAND() LIMIT :limit", nativeQuery = true)
    fun findRandomProducts(limit: Int): List<Product>

    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<Product>

    @Query(value = "SELECT DISTINCT name FROM products WHERE LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) LIMIT 5", nativeQuery = true)
    fun suggestProductNames(keyword: String): List<String>
}