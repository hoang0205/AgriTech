package com.uet.agritech.config.upload

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/upload")
class UploadController(
    private val cloudinaryService: CloudinaryService
) {

    @PostMapping("/image")
    fun uploadImage(@RequestParam("file") file: MultipartFile): ResponseEntity<Map<String, String>> {
        val imageUrl = cloudinaryService.uploadFile(file)

        return ResponseEntity.ok(mapOf("imageUrl" to imageUrl))
    }
}