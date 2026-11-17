package com.pcbqms.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pcbqms.access.domain.UserAccessProfile;
import com.pcbqms.access.domain.UserProcessScope;
import com.pcbqms.access.infrastructure.persistence.UserAccessProfileMapper;
import com.pcbqms.access.infrastructure.persistence.UserProcessScopeMapper;
import com.pcbqms.common.tenancy.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InspectionAccessScopeServiceTest {
    @Mock private UserAccessProfileMapper userAccessProfileMapper;
    @Mock private UserProcessScopeMapper userProcessScopeMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void inspectorCanOnlyAccessAssignedProcesses() {
        TenantContext.set("customer-a", "inspector.a");
        UserAccessProfile profile = new UserAccessProfile();
        profile.setTenantCode("customer-a");
        profile.setUserName("inspector.a");
        profile.setRoleCode("INSPECTOR");
        profile.setStatus("ACTIVE");
        UserProcessScope scope = new UserProcessScope();
        scope.setProcessCode("FQC");
        when(userAccessProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProcessScopeMapper.selectList(any())).thenReturn(List.of(scope));
        InspectionAccessScopeService service = new InspectionAccessScopeService(userAccessProfileMapper, userProcessScopeMapper);
        ReflectionTestUtils.setField(service, "developmentMode", false);

        var access = service.currentAccess();

        assertThat(access.fullDataAccess()).isFalse();
        assertThat(access.canAccess("FQC")).isTrue();
        assertThat(access.canAccess("SOLDER_MASK")).isFalse();
    }

    @Test
    void zshTestProcessAlsoGrantsFlyingProbeRecords() {
        TenantContext.set("customer-a", "ccq");
        UserAccessProfile profile = new UserAccessProfile();
        profile.setTenantCode("customer-a");
        profile.setUserName("ccq");
        profile.setRoleCode("INSPECTOR");
        profile.setStatus("ACTIVE");
        UserProcessScope scope = new UserProcessScope();
        scope.setProcessCode("001");
        when(userAccessProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProcessScopeMapper.selectList(any())).thenReturn(List.of(scope));
        InspectionAccessScopeService service = new InspectionAccessScopeService(userAccessProfileMapper, userProcessScopeMapper);
        ReflectionTestUtils.setField(service, "developmentMode", false);

        var access = service.currentAccess();

        assertThat(access.processCodes()).containsExactlyInAnyOrder("001", "FQC");
        assertThat(access.canAccess("SOLDER_MASK")).isFalse();
    }

    @Test
    void otherTenantsDoNotGainFlyingProbeAccessFromZshProcessCode() {
        TenantContext.set("customer-b", "inspector.b");
        UserAccessProfile profile = new UserAccessProfile();
        profile.setTenantCode("customer-b");
        profile.setUserName("inspector.b");
        profile.setRoleCode("INSPECTOR");
        profile.setStatus("ACTIVE");
        UserProcessScope scope = new UserProcessScope();
        scope.setProcessCode("001");
        when(userAccessProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProcessScopeMapper.selectList(any())).thenReturn(List.of(scope));
        InspectionAccessScopeService service = new InspectionAccessScopeService(userAccessProfileMapper, userProcessScopeMapper);
        ReflectionTestUtils.setField(service, "developmentMode", false);

        assertThat(service.currentAccess().canAccess("FQC")).isFalse();
    }

    @Test
    void platformAdministratorCanPerformSupervisorOrAdministratorOperations() {
        TenantContext.set("customer-a", "platform.admin");
        UserAccessProfile profile = new UserAccessProfile();
        profile.setTenantCode("customer-a");
        profile.setUserName("platform.admin");
        profile.setRoleCode("PLATFORM_ADMIN");
        profile.setStatus("ACTIVE");
        when(userAccessProfileMapper.selectOne(any())).thenReturn(profile);
        InspectionAccessScopeService service = new InspectionAccessScopeService(userAccessProfileMapper, userProcessScopeMapper);
        ReflectionTestUtils.setField(service, "developmentMode", false);

        assertThatCode(service::requireSupervisorOrAdministrator).doesNotThrowAnyException();
    }
}
