package com.motoyav2.shared.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class CorsConfig {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public CorsWebFilter corsWebFilter() {

    CorsConfiguration config = new CorsConfiguration();

    config.setAllowedOriginPatterns(List.of(
        "http://localhost:4200",
        "http://localhost:60252",
        "https://motoya-form-bf869.web.app",
        "https://motoya-form.web.app",
        "https://motoya-admin-nube.firebaseapp.com"
    ));

    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));

    // 🔑 API pública → SIEMPRE false
    config.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source =
        new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return new CorsWebFilter(source);

  }
}
