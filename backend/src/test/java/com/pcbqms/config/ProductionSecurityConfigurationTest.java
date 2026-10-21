package com.pcbqms.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ProductionSecurityConfigurationTest.ProbeController.class)
@ContextConfiguration(classes = ProductionSecurityConfigurationTest.TestApplication.class)
@Import({ProductionSecurityConfiguration.class, ProductionSecurityConfigurationTest.ProbeController.class,
    ProductionSecurityConfigurationTest.GatewayTestConfiguration.class})
@TestPropertySource(properties = {
    "qms.security.development-mode=false",
    "qms.integration.gateway.telemetry-tenant-code=customer-b"
})
class ProductionSecurityConfigurationTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void activeTenant() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(1);
    }

    @Test
    void gatewayOnlyTelemetryPostReachesGatewayFilter() throws Exception {
        mvc.perform(post("/api/v1/equipment/telemetry").header("X-Gateway-Token", "gateway-secret"))
            .andExpect(status().isNoContent());
    }

    @Test
    void invalidAuthorizationNeverFallsBackToGateway() throws Exception {
        mvc.perform(post("/api/v1/equipment/telemetry")
                .header("X-Gateway-Token", "gateway-secret")
                .header("Authorization", "Basic invalid"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void gatewayHeaderDoesNotOpenGetOrOtherPost() throws Exception {
        mvc.perform(get("/api/v1/equipment/telemetry").header("X-Gateway-Token", "gateway-secret"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/cnc/file-requests").header("X-Gateway-Token", "gateway-secret"))
            .andExpect(status().isUnauthorized());
    }

    @RestController
    static class ProbeController {
        @PostMapping("/api/v1/equipment/telemetry")
        ResponseEntity<Void> receive() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/v1/equipment/telemetry")
        ResponseEntity<Void> list() {
            return ResponseEntity.noContent().build();
        }

        @PostMapping("/api/v1/cnc/file-requests")
        ResponseEntity<Void> other() {
            return ResponseEntity.noContent().build();
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
    static class TestApplication {
    }
}
