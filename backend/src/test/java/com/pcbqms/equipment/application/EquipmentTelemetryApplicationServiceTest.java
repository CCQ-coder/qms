package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentTelemetryEventRequest;
import com.pcbqms.equipment.api.EquipmentTelemetryRequest;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentAlert;
import com.pcbqms.equipment.domain.EquipmentBusinessEvent;
import com.pcbqms.equipment.domain.EquipmentGatewayMapping;
import com.pcbqms.equipment.domain.EquipmentRuntimeStatus;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentAlertMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentBusinessEventMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeStatusMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetryRecordMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetrySummaryMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentTelemetryApplicationServiceTest {
    @Mock private EquipmentGatewayMappingApplicationService gatewayMappingService;
    @Mock private EquipmentTelemetryRecordMapper telemetryRecordMapper;
    @Mock private EquipmentTelemetrySummaryMapper telemetrySummaryMapper;
    @Mock private EquipmentRuntimeStatusMapper runtimeStatusMapper;
    @Mock private EquipmentBusinessEventMapper businessEventMapper;
    @Mock private EquipmentAlertMapper equipmentAlertMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void storesNormalizedTelemetryAndRaisesDurableAlarmRule() {
        TenantContext.set("customer-a", "gateway.service");
        Equipment equipment = equipment();
        when(gatewayMappingService.resolve("operator-7", "cnc-01"))
            .thenReturn(new EquipmentGatewayMappingApplicationService.ResolvedEquipment(equipment, new EquipmentGatewayMapping()));
        AtomicReference<String> insertedId = new AtomicReference<>();
        when(telemetryRecordMapper.insertIfAbsent(any())).thenAnswer(call -> {
            insertedId.set(((EquipmentTelemetryRecord) call.getArgument(0)).getId());
            return 1;
        });
        when(telemetryRecordMapper.selectIdByDeduplicationKey(any(), any(), any()))
            .thenAnswer(call -> insertedId.get());
        when(runtimeStatusMapper.selectOne(any())).thenReturn(null);

        var result = service().receive(new EquipmentTelemetryRequest(List.of(event("ALARM", "ALARM", "42"))));

        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isZero();
        ArgumentCaptor<EquipmentTelemetryRecord> telemetry = ArgumentCaptor.forClass(EquipmentTelemetryRecord.class);
        verify(telemetryRecordMapper).insertIfAbsent(telemetry.capture());
        assertThat(telemetry.getValue().getTenantCode()).isEqualTo("customer-a");
        assertThat(telemetry.getValue().getEquipmentId()).isEqualTo("equipment-1");
        assertThat(telemetry.getValue().getDeduplicationKey()).contains("CNC-01|2026-09-14T09:30:00|42");
        verify(telemetrySummaryMapper).upsertMinute(any());
        verify(telemetrySummaryMapper).upsertHour(any());
        verify(runtimeStatusMapper).insert(any(EquipmentRuntimeStatus.class));
        verify(businessEventMapper).insertWithPayload(any(EquipmentBusinessEvent.class));
        verify(equipmentAlertMapper).insert(any(EquipmentAlert.class));
    }

    @Test
    void ignoresGatewayRetryWithoutChangingRuntimeStateOrRetriggeringRules() {
        TenantContext.set("customer-a", "gateway.service");
        Equipment equipment = equipment();
        when(gatewayMappingService.resolve("operator-7", "cnc-01"))
            .thenReturn(new EquipmentGatewayMappingApplicationService.ResolvedEquipment(equipment, new EquipmentGatewayMapping()));
        when(telemetryRecordMapper.insertIfAbsent(any())).thenReturn(0);

        var result = service().receive(new EquipmentTelemetryRequest(List.of(event("ALARM", "ALARM", "42"))));

        assertThat(result.acceptedCount()).isZero();
        assertThat(result.duplicateCount()).isEqualTo(1);
        verify(runtimeStatusMapper, never()).selectOne(any());
        verify(runtimeStatusMapper, never()).insert(any(EquipmentRuntimeStatus.class));
        verify(businessEventMapper, never()).insertWithPayload(any(EquipmentBusinessEvent.class));
        verify(equipmentAlertMapper, never()).insert(any(EquipmentAlert.class));
    }

    @Test
    void reportsDuplicateWhenMysqlFoundRowsReturnsOneForNoOpUpsert() {
        TenantContext.set("customer-a", "gateway.service");
        when(gatewayMappingService.resolve("operator-7", "cnc-01"))
            .thenReturn(new EquipmentGatewayMappingApplicationService.ResolvedEquipment(equipment(), new EquipmentGatewayMapping()));
        when(telemetryRecordMapper.insertIfAbsent(any())).thenReturn(1);
        when(telemetryRecordMapper.selectIdByDeduplicationKey(any(), any(), any())).thenReturn("original-row-id");

        var result = service().receive(new EquipmentTelemetryRequest(List.of(event("ALARM", "ALARM", "42"))));

        assertThat(result.acceptedCount()).isZero();
        assertThat(result.duplicateCount()).isEqualTo(1);
        verify(runtimeStatusMapper, never()).insert(any(EquipmentRuntimeStatus.class));
        verify(telemetrySummaryMapper, never()).upsertMinute(any());
    }

    private EquipmentTelemetryApplicationService service() {
        return new EquipmentTelemetryApplicationService(gatewayMappingService, telemetryRecordMapper, telemetrySummaryMapper, runtimeStatusMapper,
            businessEventMapper, equipmentAlertMapper, new ObjectMapper().findAndRegisterModules());
    }

    private Equipment equipment() {
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-a");
        equipment.setAssetNo("CNC-01");
        equipment.setName("锣机一号");
        return equipment;
    }

    private EquipmentTelemetryEventRequest event(String eventType, String status, String recordNo) {
        return new EquipmentTelemetryEventRequest("operator-7", "cnc-01", LocalDateTime.of(2026, 9, 14, 9, 30),
            eventType, status, recordNo, 7, "P100", "v2", "MAT-1", "WO-1", "BATCH-1", "张三",
            null, "RUNNING", "E-01", "主轴异常", java.util.Map.of("vendorField", "kept"));
    }
}
