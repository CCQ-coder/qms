package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetryRecordMapper;
import java.time.LocalDateTime;
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
}
