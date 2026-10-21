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

/** Establishes the server-configured tenant for gateway-only telemetry requests. */
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
        return !request.getMethod().equals("POST")
            || !request.getRequestURI().equals("/api/v1/equipment/telemetry")
            || request.getHeader("Authorization") != null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!authentication.isConfigured() || !StringUtils.hasText(tenantCode)) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "遥测网关认证未配置");
            return;
        }
        try {
            authentication.verify(request.getHeader("X-Gateway-Token"));
        } catch (AccessDeniedException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "遥测网关认证失败");
            return;
        }
        Integer activeTenantCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM qms_tenant WHERE code = ? AND status = 'ACTIVE'", Integer.class, tenantCode);
        if (activeTenantCount == null || activeTenantCount == 0) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "遥测网关所属组织已停用");
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
