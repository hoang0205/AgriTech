package com.uet.agritech.config.province

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.text.Collator
import java.util.Locale

@RestController
@RequestMapping("/api/addresses")
class AddressController(
    private val provinceRepository: ProvinceRepository,
    private val wardRepository: WardRepository
) {
    private val vietnameseCollator = Collator.getInstance(Locale("vi", "VN"))

    @GetMapping("/provinces")
    fun getProvinces(): ResponseEntity<List<Province>> {
        val provinces = provinceRepository.findAll()
            .sortedWith(compareBy(vietnameseCollator) { it.name })
        return ResponseEntity.ok(provinces)
    }

    @GetMapping("/wards/{provinceCode}")
    fun getWards(@PathVariable provinceCode: String): ResponseEntity<List<WardDTO>> {
        if (!provinceRepository.existsById(provinceCode)) {
            return ResponseEntity.notFound().build()
        }

        val wards = wardRepository.findAllByProvinceCode(provinceCode)
            .map { WardDTO(it.code, it.name) }
            .sortedWith(compareBy(vietnameseCollator) { it.name })
        return ResponseEntity.ok(wards)
    }
}

data class WardDTO(
    val code: String,
    val name: String
)