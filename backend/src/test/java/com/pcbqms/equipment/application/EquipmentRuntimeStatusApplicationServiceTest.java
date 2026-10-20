package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentRuntimeChannel;
import com.pcbqms.equipment.domain.EquipmentRuntimeStatus;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeChannelMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeStatusMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetryRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentRuntimeStatusApplicationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 9, 0);
    @Mock private EquipmentRuntimeStatusMapper runtimeMapper;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private SystemParameterMapper parameterMapper;
    @Mock private InspectionAccessScopeService accessScope;
    @Mock private EquipmentRuntimeChannelMapper channelMapper;
    @Mock private EquipmentTelemetryRecordMapper telemetryMapper;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void staleProbeOrUnreadableDirectoryIsUnknown() {
        var resolver = new FlyingProbePresenceStatusResolver();
        assertThat(resolver.resolve(channel("__FPT_NETWORK__", "ONLINE", NOW.minusMinutes(5)),
            channel("__FPT_SOURCE__", "READABLE", NOW.minusMinutes(5)), null, NOW, 300, 600).effectiveStatus())
            .isEqualTo("UNKNOWN");
        assertThat(resolver.resolve(channel("__FPT_NETWORK__", "ONLINE", NOW),
            channel("__FPT_SOURCE__", "UNREADABLE", NOW), null, NOW, 300, 600).effectiveStatus())
            .isEqualTo("UNKNOWN");
        assertThat(resolver.resolve(channel("__FPT_NETWORK__", null, NOW),
            channel("__FPT_SOURCE__", "READABLE", NOW), null, NOW, 300, 600).effectiveStatus())
            .isEqualTo("UNKNOWN");
    }

    @Test
    void freshOfflineAndRecentTxtProduceDistinctStatuses() {
        var resolver = new FlyingProbePresenceStatusResolver();
        var source = channel("__FPT_SOURCE__", "READABLE", NOW);
        var recentTxt = channel("__FPT_TXT_ACTIVITY__", "CHANGED", NOW.minusMinutes(9));
        var oldTxt = channel("__FPT_TXT_ACTIVITY__", "CHANGED", NOW.minusMinutes(10));
        assertThat(resolver.resolve(channel("__FPT_NETWORK__", "OFFLINE", NOW), source, recentTxt,
            NOW, 300, 600).effectiveStatus()).isEqualTo("OFFLINE");
        assertThat(resolver.resolve(channel("__FPT_NETWORK__", "ONLINE", NOW), source, recentTxt,
            NOW, 300, 600).effectiveStatus()).isEqualTo("RUNNING");
        assertThat(resolver.resolve(channel("__FPT_NETWORK__", "ONLINE", NOW), source, oldTxt,
            NOW, 300, 600).effectiveStatus()).isEqualTo("ONLINE");
    }

    @Test
    void presenceOnlyEquipmentAppearsAndOldTelemetryCannotOverrideOffline() {
        baseTenant();
        when(runtimeMapper.selectList(any())).thenReturn(List.of(runtime("equipment-1", NOW)));
        when(channelMapper.selectReservedByTenant("customer-b")).thenReturn(List.of(
            channel("__FPT_NETWORK__", "OFFLINE", NOW), channel("__FPT_SOURCE__", "READABLE", NOW)));
        when(telemetryMapper.selectFlyingProbeEquipmentIds("customer-b")).thenReturn(List.of());
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment("equipment-1", "customer-b"));

        var row = service().list().getFirst();

        assertThat(row.effectiveStatus()).isEqualTo("OFFLINE");
        assertThat(row.online()).isFalse();
        assertThat(row.statusSource()).isEqualTo("FLYING_PROBE");
        assertThat(row.lastProbeAt()).isEqualTo(NOW);
    }

    @Test
    void channelWithoutRuntimeTelemetryStillAppears() {
        baseTenant();
        when(runtimeMapper.selectList(any())).thenReturn(List.of());
        when(channelMapper.selectReservedByTenant("customer-b")).thenReturn(List.of(
            channel("__FPT_NETWORK__", "ONLINE", NOW), channel("__FPT_SOURCE__", "READABLE", NOW)));
        when(telemetryMapper.selectFlyingProbeEquipmentIds("customer-b")).thenReturn(List.of());
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment("equipment-1", "customer-b"));

        var row = service().list().getFirst();

        assertThat(row.equipmentId()).isEqualTo("equipment-1");
        assertThat(row.effectiveStatus()).isEqualTo("ONLINE");
        assertThat(row.lastReceivedAt()).isNull();
    }

    @Test
    void historicalFlyingProbeWithoutPresenceIsUnknownAndCacheIsTenantScoped() {
        baseTenant();
        when(runtimeMapper.selectList(any())).thenReturn(List.of(runtime("equipment-1", NOW)));
        when(channelMapper.selectReservedByTenant("customer-b")).thenReturn(List.of());
        when(telemetryMapper.selectFlyingProbeEquipmentIds("customer-b")).thenReturn(List.of("equipment-1"));
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment("equipment-1", "customer-b"));
        var service = service();

        assertThat(service.list().getFirst().effectiveStatus()).isEqualTo("UNKNOWN");
        assertThat(service.list().getFirst().effectiveStatus()).isEqualTo("UNKNOWN");
        verify(telemetryMapper, times(1)).selectFlyingProbeEquipmentIds("customer-b");
        verify(channelMapper).insertIfAbsent(any(EquipmentRuntimeChannel.class));
    }

    @Test
    void durableUnknownMarkerSurvivesLostHistoryAndMixedTelemetry() {
        baseTenant();
        when(runtimeMapper.selectList(any())).thenReturn(List.of(runtime("equipment-1", NOW)));
        when(channelMapper.selectReservedByTenant("customer-b")).thenReturn(List.of(
            channel("__FPT_NETWORK__", "UNKNOWN", LocalDateTime.of(2000, 1, 1, 0, 0))));
        when(telemetryMapper.selectFlyingProbeEquipmentIds("customer-b")).thenReturn(List.of());
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment("equipment-1", "customer-b"));

        var row = service().list().getFirst();

        assertThat(row.statusSource()).isEqualTo("FLYING_PROBE");
        assertThat(row.effectiveStatus()).isEqualTo("UNKNOWN");
        assertThat(row.online()).isFalse();
        assertThat(row.lastProbeAt()).isNull();
    }

    @Test
    void newlyIngestedFlyingProbeIsUnknownWithoutWaitingForHistoryCacheExpiry() {
        baseTenant();
        when(runtimeMapper.selectList(any())).thenReturn(List.of(runtime("equipment-1", NOW)));
        when(channelMapper.selectReservedByTenant("customer-b")).thenReturn(List.of());
        when(telemetryMapper.selectFlyingProbeEquipmentIds("customer-b")).thenReturn(List.of());
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment("equipment-1", "customer-b"));
        var service = service();

        assertThat(service.list().getFirst().statusSource()).isEqualTo("TELEMETRY");
        service.markFlyingProbeTelemetry("customer-b", "equipment-1");
        assertThat(service.list().getFirst().effectiveStatus()).isEqualTo("UNKNOWN");
        verify(telemetryMapper, times(1)).selectFlyingProbeEquipmentIds("customer-b");
    }

    @Test
    void ordinaryEquipmentKeepsTelemetryRuleAndTenantIsolation() {
        baseTenant();
        when(runtimeMapper.selectList(any())).thenReturn(List.of(runtime("equipment-1", NOW)));
        when(channelMapper.selectReservedByTenant("customer-b")).thenReturn(List.of());
        when(telemetryMapper.selectFlyingProbeEquipmentIds("customer-b")).thenReturn(List.of());
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment("equipment-1", "customer-b"));

        var row = service().list().getFirst();

        assertThat(row.statusSource()).isEqualTo("TELEMETRY");
        assertThat(row.effectiveStatus()).isEqualTo("PROCESSING");
        assertThat(row.online()).isTrue();
    }

    private void baseTenant() {
        TenantContext.set("customer-b", "inspector");
        when(accessScope.currentAccess()).thenReturn(new InspectionAccessScopeService.InspectionAccess(
            "inspector", "TENANT_ADMIN", true, Set.of()));
    }

    private EquipmentRuntimeStatusApplicationService service() {
        return new EquipmentRuntimeStatusApplicationService(runtimeMapper, equipmentMapper, parameterMapper,
            accessScope, channelMapper, telemetryMapper, CLOCK);
    }

    private Equipment equipment(String id, String tenant) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setTenantCode(tenant);
        equipment.setName("飞针机");
        equipment.setAssetNo("WTD-1");
        return equipment;
    }

    private EquipmentRuntimeStatus runtime(String id, LocalDateTime time) {
        EquipmentRuntimeStatus status = new EquipmentRuntimeStatus();
        status.setTenantCode("customer-b");
        status.setEquipmentId(id);
        status.setReportedStatus("PROCESSING");
        status.setLastDeviceTime(time);
        status.setLastReceivedAt(time);
        return status;
    }

    private EquipmentRuntimeChannel channel(String source, String state, LocalDateTime time) {
        EquipmentRuntimeChannel channel = new EquipmentRuntimeChannel();
        channel.setTenantCode("customer-b");
        channel.setEquipmentId("equipment-1");
        channel.setSourceName(source);
        channel.setReportedStatus(state);
        channel.setLastDeviceTime(time);
        channel.setLastReceivedAt(time);
        return channel;
    }
}
