package com.pcbqms.access.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.domain.UserAccessProfile;
import com.pcbqms.access.domain.UserProcessScope;
import com.pcbqms.access.infrastructure.persistence.UserAccessProfileMapper;
import com.pcbqms.access.infrastructure.persistence.UserProcessScopeMapper;
import com.pcbqms.common.tenancy.TenantContext;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Resolves a tenant user's inspection scope. Production users must be provisioned before they can view task data. */
@Service
@RequiredArgsConstructor
public class InspectionAccessScopeService {
    private final UserAccessProfileMapper userAccessProfileMapper;
    private final UserProcessScopeMapper userProcessScopeMapper;
    @Value("${qms.security.development-mode:true}")
    private boolean developmentMode;

    public InspectionAccess currentAccess() {
        String tenantCode = TenantContext.tenantCode();
        String actor = TenantContext.actor();
        UserAccessProfile profile = userAccessProfileMapper.selectOne(Wrappers.lambdaQuery(UserAccessProfile.class)
            .eq(UserAccessProfile::getTenantCode, tenantCode)
            .eq(UserAccessProfile::getUserName, actor));
        if (profile == null) {
            if (developmentMode) {
                return new InspectionAccess(actor, "TENANT_ADMIN", true, Set.of());
            }
            throw new AccessDeniedException("当前用户尚未配置 QMS 访问权限");
        }
        if (!"ACTIVE".equals(profile.getStatus())) {
            throw new AccessDeniedException("当前 QMS 用户已停用");
        }
        String role = profile.getRoleCode().toUpperCase(Locale.ROOT);
        boolean fullDataAccess = "PLATFORM_ADMIN".equals(role) || "TENANT_ADMIN".equals(role) || "SUPERVISOR".equals(role);
        Set<String> processCodes = fullDataAccess ? Set.of() : userProcessScopeMapper.selectList(
                Wrappers.lambdaQuery(UserProcessScope.class)
                    .eq(UserProcessScope::getTenantCode, tenantCode)
                    .eq(UserProcessScope::getUserName, actor))
            .stream().map(UserProcessScope::getProcessCode).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new InspectionAccess(actor, role, fullDataAccess, processCodes);
    }

    public void requireProcessAccess(String processCode) {
        InspectionAccess access = currentAccess();
        if (!access.canAccess(processCode)) {
            throw new AccessDeniedException("无权查看或提交该工序的检验数据：" + processCode);
        }
    }

    public void requireTenantAdministrator() {
        String roleCode = currentAccess().roleCode();
        if (!"PLATFORM_ADMIN".equals(roleCode) && !"TENANT_ADMIN".equals(roleCode)) {
            throw new AccessDeniedException("仅租户管理员可维护用户与工序权限");
        }
    }

    public boolean isPlatformAdministrator() {
        return "PLATFORM_ADMIN".equals(currentAccess().roleCode());
    }

    public void requireSupervisorOrAdministrator() {
        String roleCode = currentAccess().roleCode();
        if (!"PLATFORM_ADMIN".equals(roleCode) && !"TENANT_ADMIN".equals(roleCode) && !"SUPERVISOR".equals(roleCode)) {
            throw new AccessDeniedException("仅平台主管理员、租户管理员或主管可执行该管理操作");
        }
    }

    public record InspectionAccess(String userName, String roleCode, boolean fullDataAccess, Set<String> processCodes) {
        public boolean canAccess(String processCode) {
            return fullDataAccess || processCodes.contains(processCode);
        }
    }
}
