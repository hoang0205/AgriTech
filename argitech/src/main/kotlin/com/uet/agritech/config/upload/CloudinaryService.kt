package com.uet.agritech.config.upload

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException

@Service
class CloudinaryService(
    @Value("\${cloudinary.cloud_name}") private val cloudName: String,
    @Value("\${cloudinary.api_key}") private val apiKey: String,
    @Value("\${cloudinary.api_secret}") private val apiSecret: String
) {
    private val cloudinary: Cloudinary by lazy {
        Cloudinary(
            ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
            )
        )
    }

    fun uploadFile(file: MultipartFile): String {
        try {
            val uploadResult = cloudinary.uploader().upload(file.bytes, ObjectUtils.emptyMap())

            return uploadResult["secure_url"].toString()
        } catch (e: IOException) {
            throw RuntimeException("Lỗi khi upload ảnh: ${e.message}")
        }
    }
}