package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentTelemetryHistoryQuery;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.domain.EquipmentTelemetryProjectionStatus;
import com.pcbqms.equipment.domain.FinishedTestDataWindow;
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
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

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
            telemetryRecordMapper, equipmentMapper, workOrderMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
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
        record.setDeviceTime(LocalDateTime.of(2026, 11, 19, 9, 58));
        record.setRawPayload("{\"attributes\":{\"testedBoardCount\":6,\"goodBoardCount\":5,\"badBoardCount\":1,\"correctedDeviceTime\":\"2026-11-19T09:48:00\"}}");
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
            telemetryRecordMapper, equipmentMapper, workOrderMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
        ).listRecent(10).getFirst();
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(item);

        assertThat(json.path("materialNo").asText()).isEqualTo("hp009209a-zc");
        assertThat(json.path("workOrderNo").asText()).isEqualTo("WO-123");
        assertThat(json.path("batchNo").asText()).isEqualTo("B-456");
        assertThat(json.path("goodBoardCount").asInt()).isEqualTo(5);
        assertThat(json.path("badBoardCount").asInt()).isEqualTo(1);
        assertThat(item.deviceTime()).isEqualTo(LocalDateTime.of(2026, 11, 19, 9, 58));
        assertThat(json.path("correctedDeviceTime").asText()).isEqualTo("2026-11-19T09:48:00");
    }

    @Test
    void rejectsInvalidClockCorrectionWithoutChangingRawTime() {
        TenantContext.set("customer-b", "admin");
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId("probe-invalid-clock");
        record.setEquipmentId("equipment-1");
        record.setEventType("FLYING_PROBE_TEST_COUNTERS");
        record.setDeviceTime(LocalDateTime.of(2026, 11, 19, 9, 58));
        record.setRawPayload("{\"attributes\":{\"correctedDeviceTime\":\"not-a-time\"}}");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of()));
        when(telemetryRecordMapper.selectRecentByTenant(eq("customer-b"), anyInt())).thenReturn(List.of(record));
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment);

        var item = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, workOrderMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
        ).listRecent(10).getFirst();
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(item);

        assertThat(item.deviceTime()).isEqualTo(LocalDateTime.of(2026, 11, 19, 9, 58));
        assertThat(json.path("correctedDeviceTime").isNull()).isTrue();
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
            telemetryRecordMapper, equipmentMapper, workOrderMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 20,
            "FLYING_PROBE_TEST_COUNTERS", " 26100985 ", " SO-456 ", " P2L018680B ", null, null));

        assertThat(mockingDetails(telemetryRecordMapper).getInvocations()).singleElement().satisfies(invocation ->
            assertThat(Arrays.asList(invocation.getArguments()))
                .contains("FLYING_PROBE_TEST_COUNTERS", "26100985", "SO-456", "P2L018680B")
        );
    }

    @Test
    void numberedHistoryReadsOnlySelectedRowsAndReturnsTotalOnRequest() {
        TenantContext.set("customer-b", "admin");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId("page-three-record");
        record.setEquipmentId("equipment-1");
        record.setEventType("FLYING_PROBE_TEST_COUNTERS");
        record.setTotalCount(43L);
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of()));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));
        when(telemetryRecordMapper.selectHistory(eq("customer-b"), isNull(), isNull(), any(), any(),
            isNull(), isNull(), eq(10), eq("FLYING_PROBE_TEST_COUNTERS"), isNull(), isNull(), isNull(), eq(20L), eq(true)))
            .thenReturn(List.of(record));

        var service = new EquipmentTelemetryQueryApplicationService(telemetryRecordMapper, equipmentMapper,
            workOrderMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules());
        var result = service.pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 10,
            "FLYING_PROBE_TEST_COUNTERS", null, null, 3, true));

        assertThat(result.total()).isEqualTo(43L);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.items()).extracting(item -> item.id()).containsExactly("page-three-record");
        verify(telemetryRecordMapper).selectHistory(eq("customer-b"), isNull(), isNull(), any(), any(),
            isNull(), isNull(), eq(10), eq("FLYING_PROBE_TEST_COUNTERS"), isNull(), isNull(), isNull(), eq(20L), eq(true));
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
            eq("FLYING_PROBE_TEST_COUNTERS"), any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(latest, older));
        when(telemetryRecordMapper.selectProjectionStatuses("customer-b", List.of("probe-latest")))
            .thenReturn(List.of());

        var page = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, workOrderMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 1, "FLYING_PROBE_TEST_COUNTERS", null, null));

        String cursor = new String(Base64.getUrlDecoder().decode(page.nextCursor()), StandardCharsets.UTF_8);
        assertThat(cursor).startsWith("2026-11-01T09:00");
    }

    @Test
    void flyingProbeHistoryNeverQueriesBeforeTheDataStart() {
        TenantContext.set("customer-b", "admin");
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of()));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));

        new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, workOrderMapper, inspectionAccessScopeService, new ObjectMapper()
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null,
            LocalDateTime.of(2024, 1, 1, 0, 0), null, null, 20, "FLYING_PROBE_TEST_COUNTERS", null, null));

        assertThat(mockingDetails(telemetryRecordMapper).getInvocations()).singleElement().satisfies(invocation ->
            assertThat(invocation.getArguments()[3]).isEqualTo(FinishedTestDataWindow.START));
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
        record.setWorkOrderNo("WO-123");
        record.setReceivedAt(LocalDateTime.of(2026, 10, 22, 9, 30));
        WorkOrder workOrder = new WorkOrder();
        workOrder.setWorkOrderNo("WO-123");
        workOrder.setCustomerOrderNo("SO-456");
        EquipmentTelemetryProjectionStatus projection = new EquipmentTelemetryProjectionStatus();
        projection.setTelemetryId("telemetry-1");
        projection.setStatus("RETRYABLE");
        projection.setReason("客户品号精确匹配生产指令数量为 0，必须恰好为 1");

        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "ADMIN", true, Set.of()));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(equipment));
        when(telemetryRecordMapper.selectHistory(eq("customer-b"), any(), any(), any(), any(), any(), any(), anyInt(),
            eq("FLYING_PROBE_TEST_COUNTERS"), any(), any(), any(), any(), anyBoolean())).thenReturn(List.of(record));
        when(telemetryRecordMapper.selectProjectionStatuses("customer-b", List.of("telemetry-1")))
            .thenReturn(List.of(projection));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));

        var page = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, workOrderMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 20, "FLYING_PROBE_TEST_COUNTERS", null, null));

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.fqcProjectionStatus()).isEqualTo("RETRYABLE");
            assertThat(item.fqcProjectionReason()).contains("匹配生产指令数量为 0");
            assertThat(item.fqcInspectionRecordId()).isNull();
            assertThat(item.customerOrderNo()).isEqualTo("SO-456");
        });
    }

    @Test
    void latestFlyingProbeSnapshotsOnlyIncludeAccessibleFinishedTestEquipment() {
        TenantContext.set("customer-b", "inspector");
        Equipment visible = new Equipment();
        visible.setId("visible");
        visible.setTenantCode("customer-b");
        visible.setAssetNo("WTD-1");
        visible.setEquipmentType("成品测试设备");
        visible.setProcessCode("FQC");
        Equipment hidden = new Equipment();
        hidden.setId("hidden");
        hidden.setEquipmentType("成品测试设备");
        hidden.setProcessCode("OTHER");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("inspector", "INSPECTOR", false, Set.of("FQC")));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(visible, hidden));
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId("probe-1");
        record.setEquipmentId("visible");
        record.setEventType("FLYING_PROBE_TEST_COUNTERS");
        when(telemetryRecordMapper.selectLatestFlyingProbeByEquipment(eq("customer-b"), eq(List.of("visible")), any(), any(), any()))
            .thenReturn(List.of(record));
        when(telemetryRecordMapper.selectProjectionStatuses("customer-b", List.of("probe-1"))).thenReturn(List.of());

        var result = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, workOrderMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
        ).latestFlyingProbeSnapshots();

        assertThat(result).singleElement().satisfies(item -> assertThat(item.equipmentCode()).isEqualTo("WTD-1"));
    }
}
