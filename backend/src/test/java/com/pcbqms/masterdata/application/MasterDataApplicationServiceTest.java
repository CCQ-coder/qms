package com.pcbqms.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.masterdata.api.CreateProductRequest;
import com.pcbqms.masterdata.domain.ProductMaster;
import com.pcbqms.masterdata.infrastructure.persistence.ProcessMasterMapper;
import com.pcbqms.masterdata.infrastructure.persistence.ProductMasterMapper;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MasterDataApplicationServiceTest {
    @Mock private ProductMasterMapper productMasterMapper;
    @Mock private ProcessMasterMapper processMasterMapper;
    @Mock private SystemParameterMapper systemParameterMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;
    @Captor private ArgumentCaptor<ProductMaster> productCaptor;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createsProductInsideCurrentTenant() {
        TenantContext.set("customer-b", "admin.b");
        when(productMasterMapper.selectCount(any())).thenReturn(0L);
        MasterDataApplicationService service = new MasterDataApplicationService(productMasterMapper, processMasterMapper,
            systemParameterMapper, inspectionAccessScopeService);

        var response = service.createProduct(new CreateProductRequest("PCB-100", "高频板", "Rogers / 1.0 mm"));

        verify(productMasterMapper).insert(productCaptor.capture());
        ProductMaster saved = productCaptor.getValue();
        assertThat(saved.getTenantCode()).isEqualTo("customer-b");
        assertThat(saved.getCreatedBy()).isEqualTo("admin.b");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.productCode()).isEqualTo("PCB-100");
        verify(inspectionAccessScopeService).requireTenantAdministrator();
    }

    @Test
    void rejectsProductCodeThatDoesNotMatchTenantRule() {
        TenantContext.set("customer-b", "admin.b");
        SystemParameter enabled = parameter("master-data.product-code.validation-enabled", "true");
        SystemParameter pattern = parameter("master-data.product-code.pattern", "PCB-[0-9]{3}");
        when(systemParameterMapper.selectOne(any())).thenReturn(enabled, pattern);
        MasterDataApplicationService service = new MasterDataApplicationService(productMasterMapper, processMasterMapper,
            systemParameterMapper, inspectionAccessScopeService);

        assertThatThrownBy(() -> service.createProduct(new CreateProductRequest("INVALID", "高频板", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("料号规则");
    }

    private SystemParameter parameter(String key, String value) {
        SystemParameter parameter = new SystemParameter();
        parameter.setParameterKey(key);
        parameter.setParameterValue(value);
        return parameter;
    }
}
