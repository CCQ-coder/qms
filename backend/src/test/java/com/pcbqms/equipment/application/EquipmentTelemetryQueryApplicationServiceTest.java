package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentTelemetryHistoryQuery;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.domain.EquipmentTelemetryProjectionStatus;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetryRecordMapper;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentTelemetryQueryApplicationServiceTest {
    @Mock private EquipmentTelemetryRecordMapper telemetryRecordMapper;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;
    @Mock private WorkOrderMapper workOrderMapper;

    @Test
    void historyUsesProjectedProductionOrderAndItsInternalNumber() {
        TenantContext.set("customer-b", "admin");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId("probe-1");
        record.setEquipmentId("equipment-1");
        record.setEventType("FLYING_PROBE_TEST_COUNTERS");
        record.setDeviceTime(LocalDateTime.of(2026, 11, 3, 9, 0));
        record.setReceivedAt(record.getDeviceTime());
        EquipmentTelemetryProjectionStatus projection = new EquipmentTelemetryProjectionStatus();
        projection.setTelemetryId("probe-1");
        projection.setWorkOrderNo("WO-1");
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        order.setInternalProductNo("IN-1");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "TENANT_ADMIN", true, Set.of()));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));
        when(telemetryRecordMapper.selectHistory(eq("customer-b"), any(), any(), any(), any(), any(), any(), anyInt(),
            eq("FLYING_PROBE_TEST_COUNTERS"), any(), any(), any(), eq(false))).thenReturn(List.of(record));
        when(telemetryRecordMapper.selectProjectionStatuses("customer-b", List.of("probe-1"))).thenReturn(List.of(projection));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(order));

        var item = new EquipmentTelemetryQueryApplicationService(telemetryRecordMapper, equipmentMapper,
            inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules(), workOrderMapper)
            .pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 20,
                "FLYING_PROBE_TEST_COUNTERS", null, null, null, null)).items().getFirst();

        assertThat(item.workOrderNo()).isEqualTo("WO-1");
        assertThat(item.internalProductNo()).isEqualTo("IN-1");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void returnsRecentRecordsOnlyForEquipmentInTheCurrentAccessScope() {
        TenantContext.set("demo-pcb", "demo.inspector");
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId("telemetry-1");
        record.setEquipmentId("equipment-1");
        record.setMachineName("WIN-C53UCJC3VOM");
        record.setEquipmentStatus("PROCESSING");
        record.setDeviceTime(LocalDateTime.of(2026, 9, 22, 13, 38, 42));
        record.setReceivedAt(LocalDateTime.of(2026, 9, 22, 13, 38, 43));

        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("demo-pcb");
        equipment.setAssetNo("BTF-001");
        equipment.setName("标特福锣机");
        equipment.setProcessCode("ROUTING");

        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("demo.inspector", "INSPECTOR", false, Set.of("ROUTING"))
        );
        when(telemetryRecordMapper.selectRecentByTenant(eq("demo-pcb"), anyInt())).thenReturn(List.of(record));
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment);

        var result = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules(), workOrderMapper
        ).listRecent(999);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.equipmentCode()).isEqualTo("BTF-001");
            assertThat(item.machineName()).isEqualTo("WIN-C53UCJC3VOM");
            assertThat(item.status()).isEqualTo("PROCESSING");
        });
    }

    @Test
    void exposesFlyingProbeCountersAndMaterialFromPersistedTelemetry() {
        TenantContext.set("customer-b", "admin");
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId("probe-1");
        record.setEquipmentId("equipment-1");
        record.setEventType("FLYING_PROBE_TEST_COUNTERS");
        record.setMaterialNo("hp009209a-zc");
        record.setWorkOrderNo("WO-123");
        record.setBatchNo("B-456");
        record.setMachiningCount(6);
        record.setRawPayload("{\"attributes\":{\"testedBoardCount\":6,\"goodBoardCount\":5,\"badBoardCount\":1}}");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        equipment.setAssetNo("FPT-1");
        equipment.setName("飞针机");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of())
        );
        when(telemetryRecordMapper.selectRecentByTenant(eq("customer-b"), anyInt())).thenReturn(List.of(record));
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment);

        var item = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules(), workOrderMapper
        ).listRecent(10).getFirst();
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(item);

        assertThat(json.path("materialNo").asText()).isEqualTo("hp009209a-zc");
        assertThat(json.path("workOrderNo").asText()).isEqualTo("WO-123");
        assertThat(json.path("batchNo").asText()).isEqualTo("B-456");
        assertThat(json.path("goodBoardCount").asInt()).isEqualTo(5);
        assertThat(json.path("badBoardCount").asInt()).isEqualTo(1);
    }

    @Test
    void convertsEveryZhongboReportFieldWithoutAssumingGoodBadBoardCounts() {
        TenantContext.set("customer-b", "admin");
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId("zhongbo-1");
        record.setEquipmentId("equipment-1");
        record.setEventType("ZHONGBO_TEST_REPORT");
        record.setMachineName("0002");
        record.setEquipmentStatus("PROCESSING");
        record.setMaterialNo("19250");
        record.setSourceRecordNo("ZB-1");
        record.setRawPayload("""
            {"attributes":{"PortNumber":"19250","PanelsTested":1,"ErrorTested":0,"OpenErrors":0,
            "ShortErrors":0,"totalPoints":22000,"utilizationRate":8.64,"utilizationDuration":285,
            "Counts":1,"openThresh":20.0,"shortThresh":30000000.0,"hit":4744}}
            """);
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        equipment.setAssetNo("ZBX-2");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of()));
        when(telemetryRecordMapper.selectRecentByTenant(eq("customer-b"), anyInt())).thenReturn(List.of(record));
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment);

        var item = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules(), workOrderMapper
        ).listRecent(10).getFirst();

        assertThat(item.zhongboTestReport()).satisfies(report -> {
            assertThat(report.portNumber()).isEqualTo("19250");
            assertThat(report.panelsTested()).isEqualTo(1L);
            assertThat(report.errorTested()).isZero();
            assertThat(report.openErrors()).isZero();
            assertThat(report.shortErrors()).isZero();
            assertThat(report.totalPoints()).isEqualTo(22000L);
            assertThat(report.utilizationRate()).isEqualByComparingTo("8.64");
            assertThat(report.utilizationDuration()).isEqualByComparingTo("285");
            assertThat(report.counts()).isEqualTo(1L);
            assertThat(report.openThresh()).isEqualByComparingTo("20.0");
            assertThat(report.shortThresh()).isEqualByComparingTo("30000000.0");
            assertThat(report.hit()).isEqualTo(4744L);
        });
        assertThat(item.goodBoardCount()).isNull();
        assertThat(item.badBoardCount()).isNull();
        assertThat(item.rawPayload()).contains("\"PortNumber\":\"19250\"");
    }

    @Test
    void appliesEventTypeBeforeFetchingAHistoryPage() {
        TenantContext.set("customer-b", "admin");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of())
        );
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));

        new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules(), workOrderMapper
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 20, "FLYING_PROBE_TEST_COUNTERS", null, null, null, null));

        assertThat(mockingDetails(telemetryRecordMapper).getInvocations()).singleElement().satisfies(invocation ->
            assertThat(Arrays.asList(invocation.getArguments())).contains("FLYING_PROBE_TEST_COUNTERS")
        );
    }

    @Test
    void zhongboHistoryHasNoDefaultRawTelemetryCutoff() {
        TenantContext.set("customer-b", "admin");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of()));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));

        new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules(), workOrderMapper
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 20, "ZHONGBO_TEST_REPORT", null, null, null, null));

        verify(telemetryRecordMapper).selectHistory(eq("customer-b"), isNull(), isNull(), isNull(), isNull(),
            isNull(), isNull(), eq(21), eq("ZHONGBO_TEST_REPORT"), isNull(), isNull(), isNull(), eq(false));
    }

    @Test
    void zhongboHistoryPassesFactoryNumberAndUnmatchedFilter() {
        TenantContext.set("customer-b", "admin");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of()));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));

        new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService,
            new ObjectMapper().findAndRegisterModules(), workOrderMapper
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 20,
            "ZHONGBO_TEST_REPORT", null, null, " 02PT42777 ", true));

        verify(telemetryRecordMapper).selectHistory(eq("customer-b"), isNull(), isNull(), isNull(), isNull(),
            isNull(), isNull(), eq(21), eq("ZHONGBO_TEST_REPORT"), isNull(), isNull(), eq("02PT42777"), eq(true));
    }

    @Test
    void finishedTestHistoryPassesSelectedDetectionDateRangeToQuery() {
        TenantContext.set("customer-b", "admin");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "TENANT_ADMIN", true, Set.of()));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));
        LocalDateTime from = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 9, 23, 59, 59);

        new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService,
            new ObjectMapper().findAndRegisterModules(), workOrderMapper
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, from, to, null, 20, "FINISHED_TEST", null, null, null, null));

        verify(telemetryRecordMapper).selectHistory(eq("customer-b"), isNull(), isNull(), eq(from), eq(to),
            isNull(), isNull(), eq(21), eq("FINISHED_TEST"), isNull(), isNull(), isNull(), eq(false));
    }

    @Test
    void flyingProbeHistoryCursorUsesDeviceDetectionTime() {
        TenantContext.set("customer-b", "admin");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        equipment.setAssetNo("WTD-1");
        EquipmentTelemetryRecord latest = new EquipmentTelemetryRecord();
        latest.setId("probe-latest");
        latest.setEquipmentId("equipment-1");
        latest.setEventType("FLYING_PROBE_TEST_COUNTERS");
        latest.setDeviceTime(LocalDateTime.of(2026, 11, 1, 9, 0));
        latest.setReceivedAt(LocalDateTime.of(2026, 11, 2, 15, 0));
        EquipmentTelemetryRecord older = new EquipmentTelemetryRecord();
        older.setId("probe-older");
        older.setEquipmentId("equipment-1");
        older.setEventType("FLYING_PROBE_TEST_COUNTERS");

        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of()));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));
        when(telemetryRecordMapper.selectHistory(eq("customer-b"), any(), any(), any(), any(), any(), any(), anyInt(),
            eq("FLYING_PROBE_TEST_COUNTERS"), any(), any(), any(), eq(false))).thenReturn(List.of(latest, older));
        when(telemetryRecordMapper.selectProjectionStatuses("customer-b", List.of("probe-latest")))
            .thenReturn(List.of());

        var page = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules(), workOrderMapper
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 1, "FLYING_PROBE_TEST_COUNTERS", null, null, null, null));

        String cursor = new String(Base64.getUrlDecoder().decode(page.nextCursor()), StandardCharsets.UTF_8);
        assertThat(cursor).startsWith("2026-11-01T09:00");
    }

    @Test
    void includesFlyingProbeConversionDiagnosisInHistoryPage() {
        TenantContext.set("customer-b", "admin");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        equipment.setAssetNo("WTD-1");
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId("telemetry-1");
        record.setEquipmentId("equipment-1");
        record.setEventType("FLYING_PROBE_TEST_COUNTERS");
        record.setReceivedAt(LocalDateTime.of(2026, 10, 22, 9, 30));
        EquipmentTelemetryProjectionStatus projection = new EquipmentTelemetryProjectionStatus();
        projection.setTelemetryId("telemetry-1");
        projection.setStatus("RETRYABLE");
        projection.setReason("客户品号精确匹配生产指令数量为 0，必须恰好为 1");

        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of()));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));
        when(telemetryRecordMapper.selectHistory(eq("customer-b"), any(), any(), any(), any(), any(), any(), anyInt(),
            eq("FLYING_PROBE_TEST_COUNTERS"), any(), any(), any(), eq(false))).thenReturn(List.of(record));
        when(telemetryRecordMapper.selectProjectionStatuses("customer-b", List.of("telemetry-1")))
            .thenReturn(List.of(projection));

        var page = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules(), workOrderMapper
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 20, "FLYING_PROBE_TEST_COUNTERS", null, null, null, null));

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.fqcProjectionStatus()).isEqualTo("RETRYABLE");
            assertThat(item.fqcProjectionReason()).contains("匹配生产指令数量为 0");
            assertThat(item.fqcInspectionRecordId()).isNull();
        });
    }
}
