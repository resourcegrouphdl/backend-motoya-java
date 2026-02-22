package com.motoyav2.shared.config;

import com.google.cloud.spring.data.firestore.repository.config.EnableReactiveFirestoreRepositories;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableReactiveFirestoreRepositories(basePackages = "com.motoyav2")
public class FirestoreConfig {
}
