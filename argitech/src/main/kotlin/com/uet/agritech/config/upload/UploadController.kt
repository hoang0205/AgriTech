package com.uet.agritech.config.upload

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/upload")
class UploadController(
    private val cloudinaryService: CloudinaryService
) {

    @PostMapping(
        value = ["/images"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadMultipleImages(
        @RequestParam("files") files: List<MultipartFile>
    ): ResponseEntity<List<String>> {

        if (files.isEmpty()) {
            return ResponseEntity.badRequest().build()
        }

        val uploadedUrls = files.map { file ->
            cloudinaryService.uploadFile(file)
        }

        return ResponseEntity.ok(uploadedUrls)
    }
}