package com.pcbqms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentTelemetryController;
import com.pcbqms.equipment.api.EquipmentTelemetryRequest;
import com.pcbqms.equipment.api.EquipmentTelemetryResponse;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import com.pcbqms.equipment.application.EquipmentTelemetryQueryApplicationService;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EquipmentTelemetryController.class)
@ContextConfiguration(classes = ProductionTelemetryGatewayControllerTest.TestApplication.class)
@TestPropertySource(properties = {
    "qms.security.development-mode=false",
    "qms.integration.gateway.telemetry-tenant-code=customer-b"
})
class ProductionTelemetryGatewayControllerTest {
    private static final String BODY = """
        {"events":[{"machineName":"WTD-1","deviceTime":"2026-10-19T12:00:00",
        "eventType":"EXPOSURE_RESULT","recordNo":"FPT-test-1"}]}
        """;

    @Autowired private MockMvc mvc;
    @MockBean private JdbcTemplate jdbcTemplate;
    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private EquipmentTelemetryApplicationService telemetryService;
    @MockBean private EquipmentTelemetryQueryApplicationService telemetryQueryService;

    @Test
    void gatewayOnlyRequestReachesRealControllerInConfiguredTenant() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(1);
        when(telemetryService.receive(any(EquipmentTelemetryRequest.class))).thenAnswer(invocation -> {
            assertThat(TenantContext.tenantCode()).isEqualTo("customer-b");
            return new EquipmentTelemetryResponse(1, 0);
        });

        mvc.perform(post("/api/v1/equipment/telemetry")
                .header("X-Gateway-Token", "gateway-secret")
                .header("X-Tenant-Code", "forged-tenant")
                .contentType("application/json")
                .content(BODY))
            .andExpect(status().isOk());
    }

    @Test
    void bearerRequestStillRequiresGatewayTokenAtController() throws Exception {
        Jwt jwt = Jwt.withTokenValue("verified-token")
            .header("alg", "RS256")
            .subject("operator")
            .claim("tenant_code", "jwt-tenant")
            .build();
        when(jwtDecoder.decode("verified-token")).thenReturn(jwt);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("jwt-tenant"))).thenReturn(1);

        mvc.perform(post("/api/v1/equipment/telemetry")
                .header("Authorization", "Bearer verified-token")
                .contentType("application/json")
                .content(BODY))
            .andExpect(status().isForbidden());
        verifyNoInteractions(telemetryService);
    }

    @Test
    void bearerAndGatewayTokenKeepJwtTenantPath() throws Exception {
        Jwt jwt = Jwt.withTokenValue("verified-token")
            .header("alg", "RS256")
            .subject("operator")
            .claim("tenant_code", "jwt-tenant")
            .build();
        when(jwtDecoder.decode("verified-token")).thenReturn(jwt);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("jwt-tenant"))).thenReturn(1);
        when(telemetryService.receive(any(EquipmentTelemetryRequest.class))).thenAnswer(invocation -> {
            assertThat(TenantContext.tenantCode()).isEqualTo("jwt-tenant");
            return new EquipmentTelemetryResponse(1, 0);
        });

        mvc.perform(post("/api/v1/equipment/telemetry")
                .header("Authorization", "Bearer verified-token")
                .header("X-Gateway-Token", "gateway-secret")
                .contentType("application/json")
                .content(BODY))
            .andExpect(status().isOk());
    }

    @Configuration
    static class GatewayTestConfiguration {
        @Bean
        GatewayIntegrationAuthentication gatewayIntegrationAuthentication() {
            return new GatewayIntegrationAuthentication("gateway-secret");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ProductionSecurityConfiguration.class, EquipmentTelemetryController.class,
        GatewayTestConfiguration.class})
    static class TestApplication {
    }
}
