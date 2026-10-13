package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.pcbqms.equipment.domain.EquipmentRuntimeChannel;
import com.pcbqms.equipment.domain.EquipmentRuntimeStatus;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentAlertMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentBusinessEventMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeStatusMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeChannelMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetryRecordMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetrySummaryMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class EquipmentTelemetryApplicationServiceTest {
    @Mock private EquipmentGatewayMappingApplicationService gatewayMappingService;
    @Mock private EquipmentTelemetryRecordMapper telemetryRecordMapper;
    @Mock private EquipmentTelemetrySummaryMapper telemetrySummaryMapper;
    @Mock private EquipmentRuntimeStatusMapper runtimeStatusMapper;
    @Mock private EquipmentRuntimeChannelMapper runtimeChannelMapper;
    @Mock private EquipmentBusinessEventMapper businessEventMapper;
    @Mock private EquipmentAlertMapper equipmentAlertMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void skipsIgnoredSourcesInTheProtocolIndependentReceiver() {
        TenantContext.set("customer-a", "gateway.service");
        when(gatewayMappingService.resolve("operator-7", "cnc-01"))
            .thenThrow(new EquipmentGatewayMappingApplicationService.IgnoredEquipmentGatewayMappingException("已忽略"));
        var result = service().receive(new EquipmentTelemetryRequest(List.of(event("ALARM", "ALARM", "42"))));
        assertThat(result.acceptedCount()).isZero();
        assertThat(result.duplicateCount()).isZero();
        verify(telemetryRecordMapper, never()).insert(any());
        verify(runtimeStatusMapper, never()).selectOne(any());
    }

    @Test
    void storesNormalizedTelemetryAndRaisesDurableAlarmRule() {
        TenantContext.set("customer-a", "gateway.service");
        Equipment equipment = equipment();
        when(gatewayMappingService.resolve("operator-7", "cnc-01"))
            .thenReturn(new EquipmentGatewayMappingApplicationService.ResolvedEquipment(equipment, new EquipmentGatewayMapping()));
        when(telemetryRecordMapper.insert(any())).thenReturn(1);
        when(runtimeStatusMapper.selectOne(any())).thenReturn(null);
        when(runtimeChannelMapper.selectList(any())).thenReturn(List.of(channel("CNC-01", "ALARM")));

        var result = service().receive(new EquipmentTelemetryRequest(List.of(event("ALARM", "ALARM", "42"))));

        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isZero();
        ArgumentCaptor<EquipmentTelemetryRecord> telemetry = ArgumentCaptor.forClass(EquipmentTelemetryRecord.class);
        verify(telemetryRecordMapper).insert(telemetry.capture());
        assertThat(telemetry.getValue().getTenantCode()).isEqualTo("customer-a");
        assertThat(telemetry.getValue().getEquipmentId()).isEqualTo("equipment-1");
        assertThat(telemetry.getValue().getDeduplicationKey()).contains("CNC-01|CNC-01|2026-09-14T09:30:00|42");
        verify(telemetrySummaryMapper).upsertMinute(any());
        verify(telemetrySummaryMapper).upsertHour(any());
        verify(runtimeStatusMapper).insert(any(EquipmentRuntimeStatus.class));
        verify(runtimeChannelMapper).insert(any(EquipmentRuntimeChannel.class));
        verify(businessEventMapper).insertWithPayload(any(EquipmentBusinessEvent.class));
        verify(equipmentAlertMapper).insert(any(EquipmentAlert.class));
    }

    @Test
    void ignoresGatewayRetryWithoutChangingRuntimeStateOrRetriggeringRules() {
        TenantContext.set("customer-a", "gateway.service");
        Equipment equipment = equipment();
        when(gatewayMappingService.resolve("operator-7", "cnc-01"))
            .thenReturn(new EquipmentGatewayMappingApplicationService.ResolvedEquipment(equipment, new EquipmentGatewayMapping()));
        when(telemetryRecordMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(telemetryRecordMapper.countByDeduplicationKey("customer-a", "equipment-1", "CNC-01|CNC-01|2026-09-14T09:30:00|42"))
            .thenReturn(1);

        var result = service().receive(new EquipmentTelemetryRequest(List.of(event("ALARM", "ALARM", "42"))));

        assertThat(result.acceptedCount()).isZero();
        assertThat(result.duplicateCount()).isEqualTo(1);
        verify(runtimeStatusMapper, never()).selectOne(any());
        verify(runtimeStatusMapper, never()).insert(any(EquipmentRuntimeStatus.class));
        verify(businessEventMapper, never()).insertWithPayload(any(EquipmentBusinessEvent.class));
        verify(equipmentAlertMapper, never()).insert(any(EquipmentAlert.class));
    }

    @Test
    void propagatesOtherUniqueKeyConflictsInsteadOfTreatingThemAsGatewayRetries() {
        TenantContext.set("customer-a", "gateway.service");
        when(gatewayMappingService.resolve("operator-7", "cnc-01"))
            .thenReturn(new EquipmentGatewayMappingApplicationService.ResolvedEquipment(equipment(), new EquipmentGatewayMapping()));
        when(telemetryRecordMapper.insert(any())).thenThrow(new DuplicateKeyException("other unique key"));

        assertThatThrownBy(() -> service().receive(new EquipmentTelemetryRequest(List.of(event("ALARM", "ALARM", "42")))))
            .isInstanceOf(DuplicateKeyException.class);
        verify(runtimeStatusMapper, never()).selectOne(any());
    }

    @Test
    void keepsTwoProgramsSeparateAndDoesNotRaiseAnAlarmAgainWhenTheirReportsAlternate() {
        TenantContext.set("customer-a", "gateway.service");
        Equipment equipment = equipment();
        var resolved = new EquipmentGatewayMappingApplicationService.ResolvedEquipment(equipment, new EquipmentGatewayMapping());
        when(gatewayMappingService.resolve("operator-7", "cnc-01")).thenReturn(resolved);
        when(gatewayMappingService.resolve("operator-7", "cnc-02")).thenReturn(resolved);
        when(telemetryRecordMapper.insert(any())).thenReturn(1);
        List<EquipmentRuntimeChannel> channels = new ArrayList<>();
        AtomicInteger channelReads = new AtomicInteger();
        when(runtimeChannelMapper.selectOne(any())).thenAnswer(invocation -> switch (channelReads.getAndIncrement()) {
            case 0, 1 -> null;
            case 2 -> channels.get(0);
            default -> channels.get(1);
        });
        when(runtimeChannelMapper.insert(any(EquipmentRuntimeChannel.class))).thenAnswer(invocation -> {
            channels.add(invocation.getArgument(0));
            return 1;
        });
        when(runtimeChannelMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(channels));
        AtomicReference<EquipmentRuntimeStatus> aggregate = new AtomicReference<>();
        when(runtimeStatusMapper.selectOne(any())).thenAnswer(invocation -> aggregate.get());
        when(runtimeStatusMapper.insert(any(EquipmentRuntimeStatus.class))).thenAnswer(invocation -> {
            aggregate.set(invocation.getArgument(0));
            return 1;
        });

        var result = service().receive(new EquipmentTelemetryRequest(List.of(
            eventFor("cnc-01", "PROCESSING", "42", "PROGRAM-A"),
            eventFor("cnc-02", "ALARM", "42", "PROGRAM-B"),
            eventFor("cnc-01", "PROCESSING", "43", "PROGRAM-A"),
            eventFor("cnc-02", "ALARM", "44", "PROGRAM-B")
        )));

        assertThat(result.acceptedCount()).isEqualTo(4);
        assertThat(channels).extracting(EquipmentRuntimeChannel::getProgramNo).containsExactly("PROGRAM-A", "PROGRAM-B");
        assertThat(aggregate.get().getReportedStatus()).isEqualTo("ALARM");
        assertThat(aggregate.get().getProgramNo()).isNull();
        ArgumentCaptor<EquipmentTelemetryRecord> records = ArgumentCaptor.forClass(EquipmentTelemetryRecord.class);
        verify(telemetryRecordMapper, org.mockito.Mockito.times(4)).insert(records.capture());
        assertThat(records.getAllValues().get(0).getDeduplicationKey())
            .isNotEqualTo(records.getAllValues().get(1).getDeduplicationKey());
        verify(equipmentAlertMapper).insert(any(EquipmentAlert.class));
        verify(businessEventMapper).insertWithPayload(any(EquipmentBusinessEvent.class));
    }

    private EquipmentTelemetryApplicationService service() {
        return new EquipmentTelemetryApplicationService(gatewayMappingService, telemetryRecordMapper, telemetrySummaryMapper, runtimeStatusMapper, runtimeChannelMapper,
            businessEventMapper, equipmentAlertMapper, new ObjectMapper().findAndRegisterModules());
    }

    private EquipmentRuntimeChannel channel(String sourceName, String status) {
        EquipmentRuntimeChannel channel = new EquipmentRuntimeChannel();
        channel.setTenantCode("customer-a");
        channel.setEquipmentId("equipment-1");
        channel.setSourceName(sourceName);
        channel.setReportedStatus(status);
        channel.setLastDeviceTime(LocalDateTime.of(2026, 9, 14, 9, 30));
        channel.setLastReceivedAt(LocalDateTime.of(2026, 9, 14, 9, 30));
        return channel;
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

    private EquipmentTelemetryEventRequest eventFor(String machineName, String status, String recordNo, String programNo) {
        return new EquipmentTelemetryEventRequest("operator-7", machineName, LocalDateTime.of(2026, 9, 14, 9, 30),
            "LEGACY_MONITOR_SNAPSHOT", status, recordNo, 7, programNo, null, null, null, null, null,
            null, "11", null, null, java.util.Map.of());
    }
}
