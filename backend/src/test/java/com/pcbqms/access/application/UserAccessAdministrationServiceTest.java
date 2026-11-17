package com.pcbqms.access.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pcbqms.access.api.SaveUserAccessRequest;
import com.pcbqms.access.infrastructure.persistence.UserAccessProfileMapper;
import com.pcbqms.access.infrastructure.persistence.UserProcessScopeMapper;
import com.pcbqms.administration.api.AdministrationItemResponse;
import com.pcbqms.administration.application.AdministrationApplicationService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.config.LocalPasswordAuthenticationProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccessAdministrationServiceTest {
    @Mock private UserAccessProfileMapper profiles;
    @Mock private UserProcessScopeMapper scopes;
    @Mock private InspectionAccessScopeService accessScope;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LocalPasswordAuthenticationProperties passwordProperties;
    @Mock private AdministrationApplicationService administration;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void cannotAssignRoleThatIsInactiveInRoleManagement() {
        TenantContext.set("gy", "admin");
        when(administration.listItems("ROLE")).thenReturn(List.of(new AdministrationItemResponse(
            "role-1", "ROLE", "SUPERVISOR", "主管", null, null, "INACTIVE", 20,
            Map.of(), "system", "system", LocalDateTime.MIN, LocalDateTime.MIN)));
        var service = new UserAccessAdministrationService(profiles, scopes, accessScope,
            passwordEncoder, passwordProperties, administration);

        assertThatThrownBy(() -> service.create(new SaveUserAccessRequest(
            "new.user", "新用户", "SUPERVISOR", "ACTIVE", null, List.of("004"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("角色不受支持或已停用");
    }
}
