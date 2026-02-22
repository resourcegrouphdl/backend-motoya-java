package com.motoyav2.shared.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.project-id}")
    private String projectId;

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${firebase.auth.enabled:true}")
    private boolean authEnabled;

    @PostConstruct
    public void init() {
        if (!authEnabled) {
            log.info("Firebase Auth is disabled (firebase.auth.enabled=false). Skipping initialization.");
            return;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            try {
                FirebaseOptions.Builder builder = FirebaseOptions.builder()
                        .setProjectId(projectId);

                if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
                    log.info("Initializing Firebase with service account: {}", serviceAccountPath);
                    builder.setCredentials(GoogleCredentials.fromStream(new FileInputStream(serviceAccountPath)));
                } else {
                    log.info("Initializing Firebase with Application Default Credentials");
                    builder.setCredentials(GoogleCredentials.getApplicationDefault());
                }

                FirebaseApp.initializeApp(builder.build());
                log.info("Firebase initialized for project: {}", projectId);
            } catch (IOException e) {
                log.warn("Could not initialize Firebase: {}. Token verification will be unavailable.", e.getMessage());
            }
        }
    }

    @Bean
    @ConditionalOnProperty(name = "firebase.auth.enabled", havingValue = "true", matchIfMissing = true)
    public FirebaseAuth firebaseAuth() {
        return FirebaseAuth.getInstance();
    }
}
