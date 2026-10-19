package com.pcbqms.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String suppliedId = request.getHeader(REQUEST_ID_HEADER);
        String requestId = suppliedId != null && suppliedId.matches("[0-9a-fA-F]{32}")
            ? suppliedId.toLowerCase()
            : UUID.randomUUID().toString().replace("-", "");
        String previousId = MDC.get("requestId");
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long started = System.nanoTime();
        int status = 500;
        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } catch (IOException | ServletException | RuntimeException exception) {
            log.error("Request failed: {} {}", request.getMethod(), request.getRequestURI(), exception);
            throw exception;
        } finally {
            if (!"/actuator/health".equals(request.getRequestURI())) {
                long durationMs = (System.nanoTime() - started) / 1_000_000;
                log.info("request {} {} status={} duration={}ms", request.getMethod(),
                    request.getRequestURI(), status, durationMs);
            }
            if (previousId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousId);
            }
        }
    }
}
