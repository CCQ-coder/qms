package com.pcbqms.common.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class JwtTenantContextFilterTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    private JwtTenantContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtTenantContextFilter(jdbcTemplate);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void establishesTenantScopeFromVerifiedJwtAndClearsItAfterRequest() throws Exception {
        Jwt jwt = Jwt.withTokenValue("verified-token")
            .header("alg", "RS256")
            .subject("tester.etch")
            .claim("tenant_code", "customer-b")
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        org.mockito.Mockito.when(jdbcTemplate.queryForObject(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Integer.class),
            org.mockito.ArgumentMatchers.eq("customer-b"))).thenReturn(1);
        AtomicBoolean chained = new AtomicBoolean();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/inspection/tasks"),
            new MockHttpServletResponse(), (request, response) -> {
                chained.set(true);
                assertThat(TenantContext.tenantCode()).isEqualTo("customer-b");
                assertThat(TenantContext.actor()).isEqualTo("tester.etch");
            });

        assertThat(chained).isTrue();
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsRequestWithoutAuthenticatedJwt() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/inspection/tasks"), response,
            (request, servletResponse) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chained).isFalse();
    }

    @Test
    void skipsOnlyTelemetryPostWithoutAuthorization() throws Exception {
        AtomicBoolean chained = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/equipment/telemetry"), response,
            (request, servletResponse) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chained).isTrue();

        MockHttpServletRequest basicRequest = new MockHttpServletRequest("POST", "/api/v1/equipment/telemetry");
        basicRequest.addHeader("Authorization", "Basic example");
        chained.set(false);
        response = new MockHttpServletResponse();
        filter.doFilter(basicRequest, response, (request, servletResponse) -> chained.set(true));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chained).isFalse();
    }

    @Test
    void allowsFrontendAssetsWithoutJwt() throws Exception {
        AtomicBoolean chained = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/assets/app.js"), response,
            (request, servletResponse) -> chained.set(true));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chained).isTrue();
    }

    @Test
    void rejectsJwtWithoutTenantClaim() throws Exception {
        Jwt jwt = Jwt.withTokenValue("verified-token")
            .header("alg", "RS256")
            .subject("tester.etch")
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/inspection/tasks"), response,
            (request, servletResponse) -> { });

        assertThat(response.getStatus()).isEqualTo(403);
    }
}
