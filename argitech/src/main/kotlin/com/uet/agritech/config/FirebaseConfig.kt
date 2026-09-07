package com.uet.agritech.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource

@Configuration
class FirebaseConfig {

    @PostConstruct
    fun initFirebase() {
        if (FirebaseApp.getApps().isEmpty()) {
            val resource = ClassPathResource("serviceAccountKey.json")
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(resource.inputStream))
                .build()
            FirebaseApp.initializeApp(options)
        }
    }
}