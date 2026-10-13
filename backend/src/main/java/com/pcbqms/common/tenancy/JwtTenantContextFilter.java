package com.pcbqms.common.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Establishes tenant scope from a verified JWT only; request headers are ignored in production mode. */
public class JwtTenantContextFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbcTemplate;

    public JwtTenantContextFilter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/")
            || request.getRequestURI().equals("/hessian/monitor")
            || request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "缺少有效访问令牌");
            return;
        }
        String tenantCode = jwtAuthentication.getToken().getClaimAsString("tenant_code");
        if (!StringUtils.hasText(tenantCode)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "访问令牌缺少 tenant_code 声明");
            return;
        }
        Integer activeTenantCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM qms_tenant WHERE code = ? AND status = 'ACTIVE'", Integer.class, tenantCode);
        if (activeTenantCount == null || activeTenantCount == 0) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "所属组织已停用");
            return;
        }
        TenantContext.set(tenantCode, jwtAuthentication.getName());
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
