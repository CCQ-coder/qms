package com.pcbqms.administration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.administration.api.SaveAdministrationItemRequest;
import com.pcbqms.administration.domain.AdministrationAuditLog;
import com.pcbqms.administration.domain.AdministrationItem;
import com.pcbqms.administration.infrastructure.persistence.AdministrationAuditLogMapper;
import com.pcbqms.administration.infrastructure.persistence.AdministrationItemMapper;
import com.pcbqms.common.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdministrationApplicationServiceTest {
    @Mock private AdministrationItemMapper administrationItemMapper;
    @Mock private AdministrationAuditLogMapper administrationAuditLogMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;
    @Captor private ArgumentCaptor<AdministrationItem> itemCaptor;
    @Captor private ArgumentCaptor<AdministrationAuditLog> auditCaptor;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createsTenantScopedConfigurationAndAuditLog() {
        TenantContext.set("customer-b", "admin.b");
        when(administrationItemMapper.selectCount(any())).thenReturn(0L);
        AdministrationApplicationService service = new AdministrationApplicationService(
            administrationItemMapper,
            administrationAuditLogMapper,
            inspectionAccessScopeService,
            new ObjectMapper()
        );

        var response = service.createItem("dictionary", new SaveAdministrationItemRequest(
            "defect_level", "缺陷等级", null, "供报表筛选使用", "ACTIVE", 25,
            Map.of("values", "严重,一般")
        ));

        verify(inspectionAccessScopeService).requireTenantAdministrator();
        verify(administrationItemMapper).insert(itemCaptor.capture());
        verify(administrationAuditLogMapper).insert(auditCaptor.capture());
        assertThat(itemCaptor.getValue().getTenantCode()).isEqualTo("customer-b");
        assertThat(itemCaptor.getValue().getCatalogType()).isEqualTo("DICTIONARY");
        assertThat(itemCaptor.getValue().getItemCode()).isEqualTo("DEFECT_LEVEL");
        assertThat(itemCaptor.getValue().getAttributesJson()).contains("严重");
        assertThat(auditCaptor.getValue().getModuleCode()).isEqualTo("DICTIONARY");
        assertThat(auditCaptor.getValue().getActor()).isEqualTo("admin.b");
        assertThat(response.itemCode()).isEqualTo("DEFECT_LEVEL");
    }

    @Test
    void createsFinishedTestSummaryUnderReportsMenu() {
        TenantContext.set("customer-b", "admin.b");
        when(administrationItemMapper.selectList(any())).thenReturn(List.of());
        AdministrationApplicationService service = new AdministrationApplicationService(
            administrationItemMapper,
            administrationAuditLogMapper,
            inspectionAccessScopeService,
            new ObjectMapper()
        );

        service.listItems("menu");

        verify(administrationItemMapper, atLeastOnce()).insert(itemCaptor.capture());
        AdministrationItem summaryMenu = itemCaptor.getAllValues().stream()
            .filter(item -> "EQUIPMENT_FINISHED_TEST_SUMMARY".equals(item.getItemCode()))
            .findFirst()
            .orElseThrow();
        assertThat(summaryMenu.getParentCode()).isEqualTo("TRACE");
        assertThat(summaryMenu.getSortOrder()).isEqualTo(34);
        AdministrationItem detailMenu = itemCaptor.getAllValues().stream()
            .filter(item -> "NONCONFORMITY_DETAILS".equals(item.getItemCode()))
            .findFirst()
            .orElseThrow();
        assertThat(detailMenu.getParentCode()).isEqualTo("TRACE");
    }

    @Test
    void activeDepartmentOptionsAreAvailableWithoutAdministrationPermission() {
        TenantContext.set("customer-b", "inspector.b");
        AdministrationItem active = new AdministrationItem();
        active.setItemName("测试");
        active.setStatus("ACTIVE");
        AdministrationItem inactive = new AdministrationItem();
        inactive.setItemName("旧部门");
        inactive.setStatus("INACTIVE");
        when(administrationItemMapper.selectList(any())).thenReturn(List.of(active, inactive));

        var names = new AdministrationApplicationService(administrationItemMapper,
            administrationAuditLogMapper, inspectionAccessScopeService, new ObjectMapper())
            .activeDepartmentNames();

        assertThat(names).containsExactly("测试");
        verifyNoInteractions(inspectionAccessScopeService);
    }

    @Test
    void activeScrapReasonsAreAvailableWithoutAdministrationPermission() {
        TenantContext.set("customer-b", "inspector.b");
        AdministrationItem active = new AdministrationItem();
        active.setItemName("设备检测不良");
        active.setStatus("ACTIVE");
        AdministrationItem inactive = new AdministrationItem();
        inactive.setItemName("旧原因");
        inactive.setStatus("INACTIVE");
        when(administrationItemMapper.selectList(any())).thenReturn(List.of(active, inactive));
        AdministrationApplicationService service = new AdministrationApplicationService(
            administrationItemMapper, administrationAuditLogMapper, inspectionAccessScopeService, new ObjectMapper());

        assertThat(service.activeScrapReasonNames()).containsExactly("设备检测不良");
        verifyNoInteractions(inspectionAccessScopeService);
    }
}
