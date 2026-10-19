package com.pcbqms.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ProductionSecurityConfigurationTest.TestController.class)
@ContextConfiguration(classes = ProductionSecurityConfigurationTest.TestApplication.class)
@TestPropertySource(properties = {
    "qms.security.development-mode=false",
    "qms.integration.gateway.telemetry-tenant-code=customer-b"
})
class ProductionSecurityConfigurationTest {
    @Autowired private MockMvc mvc;
    @MockBean private JdbcTemplate jdbcTemplate;
    @MockBean private JwtDecoder jwtDecoder;

    @Test
    void gatewayOnlyTelemetryPostReachesGatewayFilter() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(1);

        mvc.perform(post("/api/v1/equipment/telemetry")
                .header("X-Gateway-Token", "gateway-secret"))
            .andExpect(status().isOk())
            .andExpect(content().string("customer-b"));
    }

    @Test
    void invalidAuthorizationNeverFallsBackToGateway() throws Exception {
        mvc.perform(post("/api/v1/equipment/telemetry")
                .header("X-Gateway-Token", "gateway-secret")
                .header("Authorization", "Basic example"))
            .andExpect(status().isUnauthorized());

        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("invalid token"));
        mvc.perform(post("/api/v1/equipment/telemetry")
                .header("X-Gateway-Token", "gateway-secret")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTelemetryUsesJwtTenant() throws Exception {
        Jwt jwt = Jwt.withTokenValue("verified-token")
            .header("alg", "RS256")
            .subject("operator")
            .claim("tenant_code", "jwt-tenant")
            .build();
        when(jwtDecoder.decode("verified-token")).thenReturn(jwt);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("jwt-tenant"))).thenReturn(1);

        mvc.perform(post("/api/v1/equipment/telemetry")
                .header("Authorization", "Bearer verified-token"))
            .andExpect(status().isOk())
            .andExpect(content().string("jwt-tenant"));
    }

    @Test
    void gatewayHeaderDoesNotOpenGetOrOtherPost() throws Exception {
        mvc.perform(get("/api/v1/equipment/telemetry")
                .header("X-Gateway-Token", "gateway-secret"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/other")
                .header("X-Gateway-Token", "gateway-secret"))
            .andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestController {
        @PostMapping("/api/v1/equipment/telemetry")
        String telemetry() {
            return TenantContext.tenantCode();
        }

        @GetMapping("/api/v1/equipment/telemetry")
        String readTelemetry() {
            return "read";
        }

        @PostMapping("/api/v1/other")
        String other() {
            return "other";
        }
    }

    @TestConfiguration
    static class GatewayTestConfiguration {
        @Bean
        GatewayIntegrationAuthentication gatewayIntegrationAuthentication() {
            return new GatewayIntegrationAuthentication("gateway-secret");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ProductionSecurityConfiguration.class, TestController.class, GatewayTestConfiguration.class})
    static class TestApplication {
    }
}
