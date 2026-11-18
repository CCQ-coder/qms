package com.pcbqms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class StaticAssetSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain staticAssetSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/assets/**")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .headers(headers -> headers.cacheControl(cache -> cache.disable()))
            .build();
    }
}
