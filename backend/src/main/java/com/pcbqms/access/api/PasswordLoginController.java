package com.pcbqms.access.api;

import com.pcbqms.access.application.LocalPasswordAuthenticationService;
import com.pcbqms.common.api.ApiResponse;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "qms.security.local-password-login", name = "enabled", havingValue = "true")
public class PasswordLoginController {
    private final LocalPasswordAuthenticationService localPasswordAuthenticationService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/tenants")
    public ApiResponse<List<LoginTenantOption>> tenants() {
        return ApiResponse.of(jdbcTemplate.query("""
            SELECT tenant.code, tenant.name
            FROM qms_tenant tenant
            WHERE tenant.status = 'ACTIVE'
              AND EXISTS (
                  SELECT 1
                  FROM qms_user_access_profile user_profile
                  WHERE user_profile.tenant_code = tenant.code
                    AND user_profile.status = 'ACTIVE'
                    AND user_profile.password_hash IS NOT NULL
                    AND user_profile.password_hash <> ''
              )
            ORDER BY tenant.login_pinned DESC, tenant.name, tenant.code
            """, (resultSet, rowNumber) -> new LoginTenantOption(
            resultSet.getString("code"), resultSet.getString("name"))));
    }

    @PostMapping("/login")
    public ApiResponse<PasswordLoginResponse> login(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.of(localPasswordAuthenticationService.login(request));
    }
}
