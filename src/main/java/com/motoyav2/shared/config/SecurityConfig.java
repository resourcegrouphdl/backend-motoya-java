package com.motoyav2.shared.config;

import com.motoyav2.shared.security.FirebaseAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;

    @Value("${firebase.auth.enabled:true}")
    private boolean authEnabled;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        ServerHttpSecurity base = http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable);

        if (authEnabled) {
            base.authorizeExchange(exchanges -> exchanges
                            .pathMatchers(HttpMethod.OPTIONS).permitAll()
                            .pathMatchers("/actuator/health").permitAll()
                            .pathMatchers("/api-docs/**", "/v3/api-docs/**").permitAll()
                            .pathMatchers("/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                            .pathMatchers("/api/cobranzas-provisional/whatsapp/webhook").permitAll()
                            .pathMatchers("/webhooks/**").permitAll()
                            .anyExchange().authenticated()
                    )
                    .addFilterAt(firebaseAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION);
        } else {
            base.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());
        }

        return base.build();
    }
}
