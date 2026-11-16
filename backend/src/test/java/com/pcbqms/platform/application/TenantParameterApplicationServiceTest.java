package com.pcbqms.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantParameterApplicationServiceTest {
    @Mock private SystemParameterMapper parameterMapper;
    @Mock private InspectionAccessScopeService accessScope;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void savesTrimmedTenantTitleAndRejectsBlankOrLongTitles() {
        TenantContext.set("customer-a", "admin");
        var service = new TenantParameterApplicationService(parameterMapper, accessScope);

        var result = service.update("ui.browser-title", "  卓思涵 QMS  ");

        ArgumentCaptor<SystemParameter> saved = ArgumentCaptor.forClass(SystemParameter.class);
        verify(parameterMapper).insert(saved.capture());
        assertThat(saved.getValue().getTenantCode()).isEqualTo("customer-a");
        assertThat(result.value()).isEqualTo("卓思涵 QMS");
        assertThatThrownBy(() -> service.update("ui.sidebar-title", " "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.update("ui.browser-title", "x".repeat(41)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
