package com.pcbqms.access.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.api.PasswordLoginRequest;
import com.pcbqms.access.api.PasswordLoginResponse;
import com.pcbqms.access.domain.UserAccessProfile;
import com.pcbqms.access.infrastructure.persistence.UserAccessProfileMapper;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.config.LocalPasswordAuthenticationProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "qms.security.local-password-login", name = "enabled", havingValue = "true")
public class LocalPasswordAuthenticationService {
    private final UserAccessProfileMapper userAccessProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder localPasswordJwtEncoder;
    private final LocalPasswordAuthenticationProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public PasswordLoginResponse login(PasswordLoginRequest request) {
        String tenantCode = request.tenantCode().trim();
        String userName = request.userName().trim();
        Integer activeTenantCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM qms_tenant WHERE code = ? AND status = 'ACTIVE'", Integer.class, tenantCode);
        if (activeTenantCount == null || activeTenantCount == 0) {
            throw new BadCredentialsException("用户名、密码或租户编码错误");
        }
        UserAccessProfile profile;
        TenantContext.set(tenantCode, userName);
        try {
            profile = userAccessProfileMapper.selectOne(Wrappers.lambdaQuery(UserAccessProfile.class)
                .eq(UserAccessProfile::getTenantCode, tenantCode)
                .eq(UserAccessProfile::getUserName, userName));
        } finally {
            TenantContext.clear();
        }
        if (profile == null || !"ACTIVE".equals(profile.getStatus())
            || !StringUtils.hasText(profile.getPasswordHash())
            || !passwordEncoder.matches(request.password(), profile.getPasswordHash())) {
            throw new BadCredentialsException("用户名、密码或租户编码错误");
        }

        long safeTtlMinutes = Math.min(Math.max(properties.getTokenTtlMinutes(), 5), 1_440);
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(safeTtlMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("pcb-qms")
            .subject(profile.getUserName())
            .audience(List.of("pcb-qms-api"))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("tenant_code", profile.getTenantCode())
            .claim("roles", List.of(profile.getRoleCode()))
            .build();
        String accessToken = localPasswordJwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new PasswordLoginResponse(accessToken, safeTtlMinutes * 60, profile.getTenantCode(),
            profile.getUserName(), StringUtils.hasText(profile.getDisplayName()) ? profile.getDisplayName() : profile.getUserName(),
            profile.getRoleCode());
    }
}
