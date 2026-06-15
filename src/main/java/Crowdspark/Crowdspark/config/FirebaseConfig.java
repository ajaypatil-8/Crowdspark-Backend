// src/main/java/Crowdspark/Crowdspark/config/FirebaseConfig.java

package Crowdspark.Crowdspark.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;

@Slf4j
@Configuration
public class FirebaseConfig {

    /**
     * Path to the Firebase service-account JSON file.
     * In production: store as a secret and point FIREBASE_SERVICE_ACCOUNT_PATH to it.
     * Example application.yml entry:
     *   firebase.service-account-path: classpath:firebase-service-account.json
     */
    @Value("${firebase.service-account-path:classpath:firebase-service-account.json}")
    private Resource serviceAccountResource;

    @PostConstruct
    public void initFirebase() {
        // Guard: don't re-initialise if the app already exists
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialised — skipping");
            return;
        }

        if (!serviceAccountResource.exists()) {
            log.warn("Firebase service-account file not found at '{}'. " +
                     "Push notifications will be disabled.", serviceAccountResource);
            return;
        }

        try {
            GoogleCredentials credentials =
                    GoogleCredentials.fromStream(serviceAccountResource.getInputStream());

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialised successfully");

        } catch (IOException e) {
            log.error("Failed to initialise Firebase Admin SDK: {}", e.getMessage(), e);
            // Non-fatal — platform works without push; just no FCM messages
        }
    }
}
