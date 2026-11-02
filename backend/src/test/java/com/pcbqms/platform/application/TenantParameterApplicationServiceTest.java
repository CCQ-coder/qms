package com.pcbqms.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import com.pcbqms.platform.api.UpdateTenantParameterRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantParameterApplicationServiceTest {
    private static final String KEY = "flying-probe.material-extract-pattern";

    @Mock private SystemParameterMapper systemParameterMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void rejectsFlyingProbeRuleWithoutExactlyOneCaptureGroup() {
        TenantContext.set("tenant-a", "admin");
        var service = new TenantParameterApplicationService(systemParameterMapper, inspectionAccessScopeService);

        assertThatThrownBy(() -> service.update(KEY, "^02PT[0-9]{5}A[0-9]{2}$"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("捕获组");
        assertThatThrownBy(() -> service.update(KEY, "^02(PT)([0-9]{5})A[0-9]{2}$"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("捕获组");
        verify(systemParameterMapper, never()).insert(any(SystemParameter.class));
    }

    @Test
    void rejectsInvalidFlyingProbeRegex() {
        TenantContext.set("tenant-a", "admin");
        var service = new TenantParameterApplicationService(systemParameterMapper, inspectionAccessScopeService);

        assertThatThrownBy(() -> service.update(KEY, "^02PT([0-9]{5}$"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("正则");
        verify(systemParameterMapper, never()).insert(any(SystemParameter.class));
    }

    @Test
    void savesValidFlyingProbeCaptureRuleForCurrentTenant() {
        TenantContext.set("tenant-a", "admin");
        var service = new TenantParameterApplicationService(systemParameterMapper, inspectionAccessScopeService);

        var response = service.update(KEY, "  ^02PT([0-9]{5})A[0-9]{2}$  ");

        ArgumentCaptor<SystemParameter> saved = ArgumentCaptor.forClass(SystemParameter.class);
        verify(systemParameterMapper).insert(saved.capture());
        assertThat(saved.getValue().getTenantCode()).isEqualTo("tenant-a");
        assertThat(saved.getValue().getParameterValue()).isEqualTo("^02PT([0-9]{5})A[0-9]{2}$");
        assertThat(response.value()).isEqualTo(saved.getValue().getParameterValue());
    }

    @Test
    void clearingFlyingProbeRuleRestoresExactMatching() {
        TenantContext.set("tenant-a", "admin");
        SystemParameter existing = new SystemParameter();
        existing.setId("parameter-1");
        existing.setParameterKey(KEY);
        existing.setParameterValue("^02PT([0-9]{5})A[0-9]{2}$");
        when(systemParameterMapper.selectOne(any())).thenReturn(existing);

        var response = new TenantParameterApplicationService(systemParameterMapper, inspectionAccessScopeService)
            .update(KEY, "  ");

        assertThat(response.value()).isEmpty();
        verify(systemParameterMapper).updateById(existing);
    }

    @Test
    void blankValuesRemainInvalidForOtherParameters() {
        TenantContext.set("tenant-a", "admin");
        var service = new TenantParameterApplicationService(systemParameterMapper, inspectionAccessScopeService);

        assertThatThrownBy(() -> service.update("master-data.product-code.pattern", "  "))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能为空");
        verify(systemParameterMapper, never()).insert(any(SystemParameter.class));
    }

    @Test
    void blankRuleCanPassRequestValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new UpdateTenantParameterRequest(""))).isEmpty();
        }
    }
}
