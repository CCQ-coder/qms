package com.pcbqms.config;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Creates the first tenant and first QMS administrator if absent. A bootstrap password only initializes
 * an account that has no password hash yet; it never replaces an existing password.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(QmsBootstrapProperties.class)
public class QmsBootstrapConfiguration {
    private final QmsBootstrapProperties properties;
    private final LocalPasswordAuthenticationProperties localPasswordAuthenticationProperties;
    private final PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner qmsBootstrapInitializer(JdbcTemplate jdbcTemplate,
                                               PlatformTransactionManager transactionManager) {
        return ignored -> {
            boolean hasTenantCode = StringUtils.hasText(properties.getTenantCode());
            boolean hasAdminUserName = StringUtils.hasText(properties.getAdminUserName());
            boolean hasAdminPassword = StringUtils.hasText(properties.getAdminPassword());
            if (!hasTenantCode && !hasAdminUserName && !hasAdminPassword) {
                return;
            }
            String tenantCode = required(properties.getTenantCode(), "qms.bootstrap.tenant-code");
            String adminUserName = required(properties.getAdminUserName(), "qms.bootstrap.admin-user-name");
            String adminPassword = localPasswordAuthenticationProperties.isEnabled()
                ? required(properties.getAdminPassword(), "qms.bootstrap.admin-password") : null;
            if (adminPassword != null && adminPassword.length() < 10) {
                throw new IllegalStateException("qms.bootstrap.admin-password 至少需要 10 位");
            }
            String tenantName = StringUtils.hasText(properties.getTenantName())
                ? properties.getTenantName().trim() : tenantCode;
            String passwordHash = adminPassword == null ? null : passwordEncoder.encode(adminPassword);
            LocalDateTime now = LocalDateTime.now();

            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.update("""
                    INSERT INTO qms_tenant (id, code, name, status, created_at)
                    VALUES (?, ?, ?, 'ACTIVE', ?)
                    ON DUPLICATE KEY UPDATE code = VALUES(code)
                    """, UUID.randomUUID().toString(), tenantCode, tenantName, now);
                jdbcTemplate.update("""
                    INSERT INTO qms_user_access_profile
                      (id, tenant_code, user_name, display_name, role_code, status, password_hash, password_changed_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'PLATFORM_ADMIN', 'ACTIVE', ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      user_name = VALUES(user_name),
                      role_code = VALUES(role_code),
                      status = VALUES(status),
                      password_changed_at = CASE WHEN password_hash IS NULL OR password_hash = '' THEN VALUES(password_changed_at) ELSE password_changed_at END,
                      password_hash = CASE WHEN password_hash IS NULL OR password_hash = '' THEN VALUES(password_hash) ELSE password_hash END
                    """, UUID.randomUUID().toString(), tenantCode, adminUserName, "部署初始化管理员", passwordHash, now, now, now);
            });
        };
    }

    private String required(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("生产部署必须设置 " + propertyName);
        }
        return value.trim();
    }
}
