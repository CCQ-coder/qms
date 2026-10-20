package com.pcbqms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.FlyingProbePresenceController;
import com.pcbqms.equipment.api.FlyingProbePresenceRequest;
import com.pcbqms.equipment.api.FlyingProbePresenceResponse;
import com.pcbqms.equipment.application.FlyingProbePresenceApplicationService;
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

@WebMvcTest(controllers = FlyingProbePresenceController.class)
@ContextConfiguration(classes = ProductionFlyingProbePresenceControllerTest.TestApplication.class)
@TestPropertySource(properties = {
    "qms.security.development-mode=false",
    "qms.integration.gateway.telemetry-tenant-code=customer-b"
})
class ProductionFlyingProbePresenceControllerTest {
    private static final String BODY = """
        {"machineName":"WTD-1","observedAt":"2026-10-20T09:00:00+08:00",
        "networkState":"ONLINE","sourceState":"READABLE"}
        """;

    @Autowired private MockMvc mvc;
    @MockBean private JdbcTemplate jdbcTemplate;
    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private FlyingProbePresenceApplicationService presenceService;

    @Test
    void gatewayRequestUsesFixedTenantAndReturnsAcceptance() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(1);
        when(presenceService.receive(any(FlyingProbePresenceRequest.class))).thenAnswer(invocation -> {
            assertThat(TenantContext.tenantCode()).isEqualTo("customer-b");
            assertThat(((FlyingProbePresenceRequest) invocation.getArgument(0)).machineName()).isEqualTo("WTD-1");
            return new FlyingProbePresenceResponse(true, "equipment-1");
        });

        mvc.perform(post("/api/v1/equipment/presence/flying-probe")
                .header("X-Gateway-Token", "gateway-secret")
                .header("X-Tenant-Code", "forged-tenant")
                .contentType("application/json")
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true))
            .andExpect(jsonPath("$.data.equipmentId").value("equipment-1"));
    }

    @Test
    void validBearerCannotChangePresenceTenant() throws Exception {
        Jwt jwt = Jwt.withTokenValue("verified-token")
            .header("alg", "RS256")
            .subject("operator")
            .claim("tenant_code", "jwt-tenant")
            .build();
        when(jwtDecoder.decode("verified-token")).thenReturn(jwt);

        mvc.perform(post("/api/v1/equipment/presence/flying-probe")
                .header("Authorization", "Bearer verified-token")
                .header("X-Gateway-Token", "gateway-secret")
                .contentType("application/json")
                .content(BODY))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(presenceService);
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
    @Import({ProductionSecurityConfiguration.class, FlyingProbePresenceController.class,
        GatewayTestConfiguration.class})
    static class TestApplication {
    }
}
