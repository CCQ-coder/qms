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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class GatewayTelemetryTenantContextFilterTest {
    @Mock private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void validGatewayRequestUsesConfiguredTenantAndClearsIt() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(1);
        var filter = filter("gateway-secret", "customer-b");
        var request = request("gateway-secret");
        var response = new MockHttpServletResponse();
        var chained = new AtomicBoolean();

        filter.doFilter(request, response, (incoming, outgoing) -> {
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
        var filter = filter("gateway-secret", "customer-b");
        for (String supplied : new String[] {null, "wrong-secret"}) {
            var response = new MockHttpServletResponse();
            var chained = new AtomicBoolean();
            filter.doFilter(request(supplied), response, (incoming, outgoing) -> chained.set(true));

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chained).isFalse();
            assertThat(response.getContentAsString()).doesNotContain("gateway-secret");
            assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void missingSharedTokenReturns503() throws Exception {
        var response = new MockHttpServletResponse();
        var chained = new AtomicBoolean();

        filter("", "customer-b").doFilter(request("any-token"), response,
            (incoming, outgoing) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chained).isFalse();
    }

    @Test
    void missingTenantConfigReturns503() throws Exception {
        var response = new MockHttpServletResponse();
        var chained = new AtomicBoolean();

        filter("gateway-secret", "").doFilter(request("gateway-secret"), response,
            (incoming, outgoing) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chained).isFalse();
    }

    @Test
    void inactiveTenantReturns403() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(0);
        var response = new MockHttpServletResponse();
        var chained = new AtomicBoolean();

        filter("gateway-secret", "customer-b").doFilter(request("gateway-secret"), response,
            (incoming, outgoing) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chained).isFalse();
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exceptionStillClearsTenant() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("customer-b"))).thenReturn(1);

        assertThatThrownBy(() -> filter("gateway-secret", "customer-b").doFilter(
            request("gateway-secret"), new MockHttpServletResponse(),
            (incoming, outgoing) -> { throw new ServletException("test failure"); }))
            .isInstanceOf(ServletException.class);
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    private GatewayTelemetryTenantContextFilter filter(String token, String tenantCode) {
        return new GatewayTelemetryTenantContextFilter(
            new GatewayIntegrationAuthentication(token), jdbcTemplate, tenantCode);
    }

    private MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest("POST", "/api/v1/equipment/telemetry");
        if (token != null) request.addHeader("X-Gateway-Token", token);
        return request;
    }
}
