package com.pcbqms.common.tenancy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FlyingProbeDeviceTenantContextFilterTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    @Test void tokenBindsTenantAndMachineAndAlwaysClearsContext() throws Exception {
        var identity = new FlyingProbeDeviceTenantContextFilter.DeviceIdentity("customer-b", "飞针01");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<FlyingProbeDeviceTenantContextFilter.DeviceIdentity>>any(), any()))
            .thenReturn(List.of(identity));
        var request = request("A".repeat(43));
        request.addHeader("X-Tenant-Code", "customer-a");
        request.addHeader("Authorization", "Basic ignored");
        new FlyingProbeDeviceTenantContextFilter(jdbc).doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            assertThat(TenantContext.tenantCode()).isEqualTo("customer-b");
            assertThat(req.getAttribute(FlyingProbeDeviceTenantContextFilter.IDENTITY_ATTRIBUTE)).isEqualTo(identity);
        });
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }
    @Test void missingUnknownOrRevokedTokenNeverReachesController() throws Exception {
        for (String token : new String[] {null, "wrong", "A".repeat(43)}) {
            var response = new MockHttpServletResponse();
            var reached = new AtomicBoolean();
            new FlyingProbeDeviceTenantContextFilter(jdbc).doFilter(request(token), response, (a,b) -> reached.set(true));
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(reached).isFalse();
        }
    }
    @Test void boundsChunkedRequestsBeforeJsonParsing() throws Exception {
        var identity = new FlyingProbeDeviceTenantContextFilter.DeviceIdentity("customer-b", "飞针01");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<FlyingProbeDeviceTenantContextFilter.DeviceIdentity>>any(), any()))
            .thenReturn(List.of(identity));
        var request = new MockHttpServletRequest("POST", "/api/v1/flying-probe/device-files") {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.addHeader("X-Device-Token", "A".repeat(43));
        request.setContent(new byte[11_500_001]);
        var response = new MockHttpServletResponse();
        new FlyingProbeDeviceTenantContextFilter(jdbc).doFilter(request, response, (a,b) -> fail("oversize reached controller"));
        assertThat(response.getStatus()).isEqualTo(413);
    }
    private MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest("POST", "/api/v1/flying-probe/device-files");
        if (token != null) request.addHeader("X-Device-Token", token);
        return request;
    }
}
