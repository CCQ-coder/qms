package com.pcbqms.common.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayTelemetryTenantContextFilterTest {
    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void validGatewayRequestUsesConfiguredTenantAndClearsIt() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(1);
        var request = telemetryRequest("gateway-secret");
        var response = new MockHttpServletResponse();
        var filter = filter("gateway-secret", "customer-b");
        AtomicBoolean chained = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> {
            chained.set(true);
            assertThat(TenantContext.tenantCode()).isEqualTo("customer-b");
            assertThat(TenantContext.actor()).isEqualTo("gateway.telemetry");
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chained).isTrue();
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingOrWrongTokenReturns401() throws Exception {
        for (String supplied : new String[] { null, "wrong-secret" }) {
            var request = telemetryRequest(supplied);
            var response = new MockHttpServletResponse();
            AtomicBoolean chained = new AtomicBoolean();

            filter("gateway-secret", "customer-b").doFilter(request, response,
                (req, res) -> chained.set(true));

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chained).isFalse();
            assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void missingSharedTokenReturns503() throws Exception {
        var response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        filter("", "customer-b").doFilter(telemetryRequest("any-token"), response,
            (req, res) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chained).isFalse();
    }

    @Test
    void missingTenantConfigReturns503() throws Exception {
        var response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        filter("gateway-secret", "").doFilter(telemetryRequest("gateway-secret"), response,
            (req, res) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chained).isFalse();
    }

    @Test
    void inactiveTenantReturns403() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(0);
        var response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        filter("gateway-secret", "customer-b").doFilter(telemetryRequest("gateway-secret"), response,
            (req, res) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chained).isFalse();
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exceptionStillClearsTenant() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(1);

        assertThatThrownBy(() -> filter("gateway-secret", "customer-b").doFilter(
            telemetryRequest("gateway-secret"), new MockHttpServletResponse(),
            (req, res) -> { throw new ServletException("downstream failed"); }))
            .isInstanceOf(ServletException.class);
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void skipsOtherRoutesAndRequestsWithAuthorization() throws Exception {
        var filter = filter("gateway-secret", "customer-b");
        for (MockHttpServletRequest request : new MockHttpServletRequest[] {
            new MockHttpServletRequest("GET", "/api/v1/equipment/telemetry"),
            new MockHttpServletRequest("POST", "/api/v1/cnc/file-requests"),
            authorizedTelemetryRequest()
        }) {
            var response = new MockHttpServletResponse();
            AtomicBoolean chained = new AtomicBoolean();
            filter.doFilter(request, response, (req, res) -> chained.set(true));
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(chained).isTrue();
        }
    }

    private GatewayTelemetryTenantContextFilter filter(String sharedToken, String tenantCode) {
        return new GatewayTelemetryTenantContextFilter(
            new GatewayIntegrationAuthentication(sharedToken), jdbcTemplate, tenantCode);
    }

    private MockHttpServletRequest telemetryRequest(String token) {
        var request = new MockHttpServletRequest("POST", "/api/v1/equipment/telemetry");
        if (token != null) request.addHeader("X-Gateway-Token", token);
        return request;
    }

    private MockHttpServletRequest authorizedTelemetryRequest() {
        var request = telemetryRequest("gateway-secret");
        request.addHeader("Authorization", "Bearer invalid");
        return request;
    }
}
