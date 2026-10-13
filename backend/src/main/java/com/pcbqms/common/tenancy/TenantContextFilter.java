package com.pcbqms.common.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Development-only tenant context. Production authentication will derive this data from signed claims.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "qms.security", name = "development-mode", havingValue = "true")
public class TenantContextFilter extends OncePerRequestFilter {

    private final String defaultTenantCode;

    public TenantContextFilter(@Value("${qms.tenancy.default-code}") String defaultTenantCode) {
        this.defaultTenantCode = defaultTenantCode;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantCode = request.getHeader("X-Tenant-Code");
        String actor = request.getHeader("X-QMS-Actor");
        TenantContext.set(
            StringUtils.hasText(tenantCode) ? tenantCode.trim() : defaultTenantCode,
            StringUtils.hasText(actor) ? actor.trim() : "demo.inspector"
        );
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
