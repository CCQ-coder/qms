package com.pcbqms.common.tenancy;

import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Establishes a fixed tenant scope for telemetry requests authenticated by the gateway token. */
public class GatewayTelemetryTenantContextFilter extends OncePerRequestFilter {
    private final GatewayIntegrationAuthentication authentication;
    private final JdbcTemplate jdbcTemplate;
    private final String tenantCode;

    public GatewayTelemetryTenantContextFilter(GatewayIntegrationAuthentication authentication,
                                               JdbcTemplate jdbcTemplate, String tenantCode) {
        this.authentication = authentication;
        this.jdbcTemplate = jdbcTemplate;
        this.tenantCode = tenantCode;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
            || !"/api/v1/equipment/telemetry".equals(request.getRequestURI())
            || request.getHeader("Authorization") != null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!authentication.isConfigured() || !StringUtils.hasText(tenantCode)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        try {
            authentication.verify(request.getHeader("X-Gateway-Token"));
        } catch (AccessDeniedException error) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Integer activeTenantCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM qms_tenant WHERE code = ? AND status = 'ACTIVE'",
            Integer.class, tenantCode);
        if (activeTenantCount == null || activeTenantCount == 0) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        TenantContext.set(tenantCode, "gateway.telemetry");
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
