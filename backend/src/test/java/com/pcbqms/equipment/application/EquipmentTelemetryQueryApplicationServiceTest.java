package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
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
import java.time.LocalDateTime;
import java.util.Arrays;
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
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
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
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
        ).listRecent(10).getFirst();
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(item);

        assertThat(json.path("materialNo").asText()).isEqualTo("hp009209a-zc");
        assertThat(json.path("workOrderNo").asText()).isEqualTo("WO-123");
        assertThat(json.path("batchNo").asText()).isEqualTo("B-456");
        assertThat(json.path("goodBoardCount").asInt()).isEqualTo(5);
        assertThat(json.path("badBoardCount").asInt()).isEqualTo(1);
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
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 20, "FLYING_PROBE_TEST_COUNTERS"));

        assertThat(mockingDetails(telemetryRecordMapper).getInvocations()).singleElement().satisfies(invocation ->
            assertThat(Arrays.asList(invocation.getArguments())).contains("FLYING_PROBE_TEST_COUNTERS")
        );
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
            eq("FLYING_PROBE_TEST_COUNTERS"))).thenReturn(List.of(record));
        when(telemetryRecordMapper.selectProjectionStatuses("customer-b", List.of("telemetry-1")))
            .thenReturn(List.of(projection));

        var page = new EquipmentTelemetryQueryApplicationService(
            telemetryRecordMapper, equipmentMapper, inspectionAccessScopeService, new ObjectMapper().findAndRegisterModules()
        ).pageHistory(new EquipmentTelemetryHistoryQuery(null, null, null, null, 20, "FLYING_PROBE_TEST_COUNTERS"));

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.fqcProjectionStatus()).isEqualTo("RETRYABLE");
            assertThat(item.fqcProjectionReason()).contains("匹配生产指令数量为 0");
            assertThat(item.fqcInspectionRecordId()).isNull();
        });
    }
}
