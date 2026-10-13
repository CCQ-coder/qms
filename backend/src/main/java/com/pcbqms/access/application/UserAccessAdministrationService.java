package com.pcbqms.access.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.api.SaveUserAccessRequest;
import com.pcbqms.access.api.UserAccessResponse;
import com.pcbqms.access.domain.UserAccessProfile;
import com.pcbqms.access.domain.UserProcessScope;
import com.pcbqms.access.infrastructure.persistence.UserAccessProfileMapper;
import com.pcbqms.access.infrastructure.persistence.UserProcessScopeMapper;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.config.LocalPasswordAuthenticationProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserAccessAdministrationService {
    private static final Set<String> ROLE_CODES = Set.of("PLATFORM_ADMIN", "TENANT_ADMIN", "SUPERVISOR", "INSPECTOR");
    private static final Set<String> STATUS_CODES = Set.of("ACTIVE", "INACTIVE");
    private final UserAccessProfileMapper userAccessProfileMapper;
    private final UserProcessScopeMapper userProcessScopeMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;
    private final PasswordEncoder passwordEncoder;
    private final LocalPasswordAuthenticationProperties localPasswordAuthenticationProperties;

    public List<UserAccessResponse> list() {
        inspectionAccessScopeService.requireTenantAdministrator();
        return userAccessProfileMapper.selectList(Wrappers.lambdaQuery(UserAccessProfile.class)
                .eq(UserAccessProfile::getTenantCode, TenantContext.tenantCode())
                .orderByAsc(UserAccessProfile::getUserName))
            .stream().map(this::toResponse).toList();
    }

    @Transactional
    public UserAccessResponse create(SaveUserAccessRequest request) {
        inspectionAccessScopeService.requireTenantAdministrator();
        String tenantCode = TenantContext.tenantCode();
        String userName = request.userName().trim();
        if (userAccessProfileMapper.selectCount(Wrappers.lambdaQuery(UserAccessProfile.class)
            .eq(UserAccessProfile::getTenantCode, tenantCode)
            .eq(UserAccessProfile::getUserName, userName)) > 0) {
            throw new IllegalArgumentException("用户已存在：" + userName);
        }
        LocalDateTime now = LocalDateTime.now();
        UserAccessProfile profile = new UserAccessProfile();
        profile.setId(UUID.randomUUID().toString());
        profile.setTenantCode(tenantCode);
        profile.setUserName(userName);
        profile.setCreatedAt(now);
        apply(profile, request, now, true);
        userAccessProfileMapper.insert(profile);
        replaceProcessScopes(profile.getUserName(), request.processCodes(), now);
        return toResponse(profile);
    }

    @Transactional
    public UserAccessResponse update(String id, SaveUserAccessRequest request) {
        inspectionAccessScopeService.requireTenantAdministrator();
        UserAccessProfile profile = requireProfile(id);
        if (!profile.getUserName().equals(request.userName().trim())) {
            throw new IllegalArgumentException("用户标识创建后不可修改");
        }
        LocalDateTime now = LocalDateTime.now();
        apply(profile, request, now, false);
        userAccessProfileMapper.updateById(profile);
        replaceProcessScopes(profile.getUserName(), request.processCodes(), now);
        return toResponse(profile);
    }

    private void apply(UserAccessProfile profile, SaveUserAccessRequest request, LocalDateTime now, boolean creating) {
        String role = request.roleCode().trim().toUpperCase(Locale.ROOT);
        String status = StringUtils.hasText(request.status()) ? request.status().trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        if (!ROLE_CODES.contains(role)) {
            throw new IllegalArgumentException("角色不受支持");
        }
        boolean platformRoleChange = "PLATFORM_ADMIN".equals(role) || "PLATFORM_ADMIN".equals(profile.getRoleCode());
        if (platformRoleChange && !inspectionAccessScopeService.isPlatformAdministrator()) {
            throw new org.springframework.security.access.AccessDeniedException("仅平台管理员可以授予或修改平台管理员权限");
        }
        if (!STATUS_CODES.contains(status)) {
            throw new IllegalArgumentException("用户状态仅支持 ACTIVE 或 INACTIVE");
        }
        List<String> processCodes = normalizedProcessCodes(request.processCodes());
        if ("INSPECTOR".equals(role) && processCodes.isEmpty()) {
            throw new IllegalArgumentException("检验员至少需要分配一个工序");
        }
        profile.setDisplayName(StringUtils.hasText(request.displayName()) ? request.displayName().trim() : null);
        profile.setRoleCode(role);
        profile.setStatus(status);
        if (localPasswordAuthenticationProperties.isEnabled()) {
            if (creating && !StringUtils.hasText(request.password())) {
                throw new IllegalArgumentException("新建用户必须设置初始密码");
            }
            if (StringUtils.hasText(request.password())) {
                String password = request.password();
                if (password.length() < 10) {
                    throw new IllegalArgumentException("密码长度至少为 10 位");
                }
                profile.setPasswordHash(passwordEncoder.encode(password));
                profile.setPasswordChangedAt(now);
            }
        }
        profile.setUpdatedAt(now);
    }

    private void replaceProcessScopes(String userName, List<String> requestedCodes, LocalDateTime now) {
        String tenantCode = TenantContext.tenantCode();
        userProcessScopeMapper.delete(Wrappers.lambdaQuery(UserProcessScope.class)
            .eq(UserProcessScope::getTenantCode, tenantCode)
            .eq(UserProcessScope::getUserName, userName));
        for (String processCode : normalizedProcessCodes(requestedCodes)) {
            UserProcessScope scope = new UserProcessScope();
            scope.setId(UUID.randomUUID().toString());
            scope.setTenantCode(tenantCode);
            scope.setUserName(userName);
            scope.setProcessCode(processCode);
            scope.setCreatedAt(now);
            userProcessScopeMapper.insert(scope);
        }
    }

    private UserAccessProfile requireProfile(String id) {
        UserAccessProfile profile = userAccessProfileMapper.selectOne(Wrappers.lambdaQuery(UserAccessProfile.class)
            .eq(UserAccessProfile::getId, id)
            .eq(UserAccessProfile::getTenantCode, TenantContext.tenantCode()));
        if (profile == null) {
            throw new IllegalArgumentException("用户不存在或无权访问");
        }
        return profile;
    }

    private UserAccessResponse toResponse(UserAccessProfile profile) {
        List<String> processCodes = userProcessScopeMapper.selectList(Wrappers.lambdaQuery(UserProcessScope.class)
                .eq(UserProcessScope::getTenantCode, profile.getTenantCode())
                .eq(UserProcessScope::getUserName, profile.getUserName())
                .orderByAsc(UserProcessScope::getProcessCode))
            .stream().map(UserProcessScope::getProcessCode).toList();
        return UserAccessResponse.from(profile, processCodes);
    }

    private List<String> normalizedProcessCodes(List<String> processCodes) {
        if (processCodes == null) {
            return List.of();
        }
        return processCodes.stream().filter(StringUtils::hasText).map(value -> value.trim().toUpperCase(Locale.ROOT))
            .distinct().toList();
    }
}
