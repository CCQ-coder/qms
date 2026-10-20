package com.pcbqms.config;

import com.pcbqms.common.tenancy.JwtTenantContextFilter;
import com.pcbqms.common.tenancy.GatewayTelemetryTenantContextFilter;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(prefix = "qms.security", name = "development-mode", havingValue = "false")
public class ProductionSecurityConfiguration {
    @Bean
    SecurityFilterChain productionSecurityFilterChain(HttpSecurity http,
                                                      JwtTenantContextFilter jwtTenantContextFilter,
                                                      GatewayIntegrationAuthentication gatewayAuthentication,
                                                      JdbcTemplate jdbcTemplate,
                                                      @Value("${qms.integration.gateway.telemetry-tenant-code:}")
                                                          String telemetryTenantCode) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/info", "/hessian/monitor").permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.svg",
                    "/manifest.webmanifest", "/runtime-config.js", "/sw.js").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/tenants").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/equipment/telemetry").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/equipment/presence/flying-probe").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .addFilterAfter(jwtTenantContextFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(new GatewayTelemetryTenantContextFilter(gatewayAuthentication, jdbcTemplate, telemetryTenantCode),
                JwtTenantContextFilter.class)
            .build();
    }

    /** Runs inside Spring Security, after the bearer token has been verified. */
    @Bean
    JwtTenantContextFilter jwtTenantContextFilter(JdbcTemplate jdbcTemplate) {
        return new JwtTenantContextFilter(jdbcTemplate);
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> scopeAuthorities = scopeConverter.convert(jwt);
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopeAuthorities == null ? List.of() : scopeAuthorities);
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                authorities.addAll(roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toSet()));
            }
            return authorities;
        });
        return converter;
    }
}
