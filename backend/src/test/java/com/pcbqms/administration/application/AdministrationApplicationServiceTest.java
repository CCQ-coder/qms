package com.pcbqms.administration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

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
    void auditLogPagesCountOnceAndReadOnlyTheRequestedRows() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), AdministrationAuditLog.class);
        TenantContext.set("customer-b", "admin.b");
        AdministrationAuditLog log = new AdministrationAuditLog();
        log.setId("log-21");
        when(administrationAuditLogMapper.selectCount(any())).thenReturn(43L);
        when(administrationAuditLogMapper.selectList(any())).thenReturn(List.of(log));
        var service = new AdministrationApplicationService(administrationItemMapper,
            administrationAuditLogMapper, inspectionAccessScopeService, new ObjectMapper());

        var first = service.pageAuditLogs(" role ", 1, 10, true);
        var later = service.pageAuditLogs(" role ", 3, 10, false);

        assertThat(first.total()).isEqualTo(43L);
        assertThat(later.total()).isNull();
        assertThat(later.items()).extracting(item -> item.id()).containsExactly("log-21");
        verify(administrationAuditLogMapper, times(1)).selectCount(any());
        var queries = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(administrationAuditLogMapper, times(2)).selectList(queries.capture());
        assertThat(queries.getAllValues().get(1).getSqlSegment()).contains("LIMIT 10 OFFSET 20");
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
    }
}
