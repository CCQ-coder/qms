package com.pcbqms.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * This is intentionally restricted to local development. Replace it with OIDC/JWT before production deployment.
 */
@Configuration
@ConditionalOnProperty(prefix = "qms.security", name = "development-mode", havingValue = "true")
public class DevelopmentSecurityConfiguration {

    @Bean
    SecurityFilterChain developmentSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .build();
    }
}

