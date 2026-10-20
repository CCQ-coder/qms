package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.FlyingProbePresenceRequest;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentGatewayMapping;
import com.pcbqms.equipment.domain.EquipmentRuntimeChannel;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeChannelMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlyingProbePresenceApplicationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T01:00:00Z"), ZoneOffset.UTC);
    @Mock private EquipmentGatewayMappingApplicationService mappingService;
    @Mock private EquipmentRuntimeChannelMapper channelMapper;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void validOffsetTimeWritesNetworkAndSourceInShanghaiTime() {
        TenantContext.set("customer-b", "gateway.telemetry");
        mappedEquipment();
        when(channelMapper.upsertIfNewer(any())).thenReturn(1);

        var response = service().receive(request("2026-10-20T01:00:00Z", null));

        assertThat(response.accepted()).isTrue();
        assertThat(response.equipmentId()).isEqualTo("equipment-1");
        var channels = ArgumentCaptor.forClass(EquipmentRuntimeChannel.class);
        verify(channelMapper, org.mockito.Mockito.times(2)).upsertIfNewer(channels.capture());
        assertThat(channels.getAllValues()).extracting(EquipmentRuntimeChannel::getSourceName)
            .containsExactly("__FPT_NETWORK__", "__FPT_SOURCE__");
        assertThat(channels.getAllValues()).allSatisfy(channel -> {
            assertThat(channel.getTenantCode()).isEqualTo("customer-b");
            assertThat(channel.getEquipmentId()).isEqualTo("equipment-1");
            assertThat(channel.getLastDeviceTime()).isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 0));
            assertThat(channel.getLastReceivedAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 0));
        });
    }

    @Test
    void olderObservationDoesNotWriteSourceOrTxtActivity() {
        TenantContext.set("customer-b", "gateway.telemetry");
        mappedEquipment();
        when(channelMapper.upsertIfNewer(any())).thenReturn(0);

        var response = service().receive(request("2026-10-20T08:59:00+08:00", "2026-10-20T08:58:00+08:00"));

        assertThat(response.accepted()).isFalse();
        verify(channelMapper).upsertIfNewer(any());
    }

    @Test
    void unknownMappingCannotWritePresence() {
        TenantContext.set("customer-b", "gateway.telemetry");
        when(mappingService.resolve(null, "WTD-1"))
            .thenThrow(new EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException("missing"));

        assertThatThrownBy(() -> service().receive(request("2026-10-20T09:00:00+08:00", null)))
            .isInstanceOf(EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException.class);
        verify(channelMapper, never()).upsertIfNewer(any());
    }

    @Test
    void staleOrFutureObservationIsRejected() {
        TenantContext.set("customer-b", "gateway.telemetry");
        assertThatThrownBy(() -> service().receive(request("2026-10-20T08:54:59+08:00", null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().receive(request("2026-10-20T09:05:01+08:00", null)))
            .isInstanceOf(IllegalArgumentException.class);
        verify(channelMapper, never()).upsertIfNewer(any());
    }

    @Test
    void txtChangeAfterProbeIsRejected() {
        TenantContext.set("customer-b", "gateway.telemetry");
        assertThatThrownBy(() -> service().receive(request("2026-10-20T09:00:00+08:00", "2026-10-20T09:00:01+08:00")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(channelMapper, never()).upsertIfNewer(any());
    }

    private void mappedEquipment() {
        Equipment equipment = new Equipment();
        equipment.setId("equipment-1");
        equipment.setTenantCode("customer-b");
        when(mappingService.resolve(null, "WTD-1"))
            .thenReturn(new EquipmentGatewayMappingApplicationService.ResolvedEquipment(equipment, new EquipmentGatewayMapping()));
    }

    private FlyingProbePresenceApplicationService service() {
        return new FlyingProbePresenceApplicationService(mappingService, channelMapper, CLOCK);
    }

    private FlyingProbePresenceRequest request(String observed, String txtChanged) {
        return new FlyingProbePresenceRequest("WTD-1", OffsetDateTime.parse(observed), "ONLINE", "READABLE",
            txtChanged == null ? null : OffsetDateTime.parse(txtChanged));
    }
}
