package com.pcbqms.integration.btfhessian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentTelemetryRequest;
import com.pcbqms.equipment.api.EquipmentTelemetryResponse;
import com.pcbqms.equipment.application.EquipmentGatewayMappingApplicationService;
import com.pcbqms.equipment.application.EquipmentGatewayUnmatchedMessageApplicationService;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BtfHessianMonitorApplicationServiceTest {
    @Mock private EquipmentTelemetryApplicationService telemetryApplicationService;
    @Mock private EquipmentGatewayMappingApplicationService gatewayMappingService;
    @Mock private EquipmentGatewayUnmatchedMessageApplicationService unmatchedMessageApplicationService;

    private BtfHessianMonitorApplicationService service;

    @BeforeEach
    void setUp() {
        BtfHessianProperties properties = new BtfHessianProperties();
        properties.setTenantCode("btf-customer");
        properties.validateAndInitialize();
        service = new BtfHessianMonitorApplicationService(
            new ObjectMapper().findAndRegisterModules(), telemetryApplicationService, gatewayMappingService, unmatchedMessageApplicationService, properties
        );
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void preservesAlarmScreenTextAsTheAlarmReason() {
        when(telemetryApplicationService.receive(any())).thenReturn(new EquipmentTelemetryResponse(1, 0));
        service.receive(payload("ALAM", "42.5").replace("\"screentext\":\"\"", "\"screentext\":\"主轴过热报警！\""));
        var request = ArgumentCaptor.forClass(EquipmentTelemetryRequest.class);
        verify(telemetryApplicationService).receive(request.capture());
        var event = request.getValue().events().getFirst();
        assertThat(event.status()).isEqualTo("ALARM");
        assertThat(event.alarmMessage()).isEqualTo("主轴过热报警！");
        assertThat(event.attributes()).containsEntry("legacyScreenText", "主轴过热报警！")
            .containsEntry("legacyScreenTextState", "PRESENT");
    }

    @Test
    void identifiesAnEmptyAlarmScreenTextWithoutInventingAReason() {
        when(telemetryApplicationService.receive(any())).thenReturn(new EquipmentTelemetryResponse(1, 0));
        service.receive(payload("ALAM", "42.5"));
        var request = ArgumentCaptor.forClass(EquipmentTelemetryRequest.class);
        verify(telemetryApplicationService).receive(request.capture());
        var event = request.getValue().events().getFirst();
        assertThat(event.alarmMessage()).isNull();
        assertThat(event.attributes()).containsEntry("legacyScreenTextState", "EMPTY");
    }

    @Test
    void identifiesAMissingAlarmScreenTextField() {
        when(telemetryApplicationService.receive(any())).thenReturn(new EquipmentTelemetryResponse(1, 0));
        service.receive(payload("ALAM", "42.5").replaceAll(",\\s*\"screentext\":\"\"", ""));
        var request = ArgumentCaptor.forClass(EquipmentTelemetryRequest.class);
        verify(telemetryApplicationService).receive(request.capture());
        assertThat(request.getValue().events().getFirst().attributes()).containsEntry("legacyScreenTextState", "MISSING");
    }

    @Test
    void generatesTheSameDeduplicationRecordForAnIdenticalRetry() {
        when(telemetryApplicationService.receive(any())).thenReturn(new EquipmentTelemetryResponse(1, 0));
        service.receive(payload("WORK", "42.5"));
        service.receive(payload("WORK", "42.5"));
        var requests = ArgumentCaptor.forClass(EquipmentTelemetryRequest.class);
        verify(telemetryApplicationService, org.mockito.Mockito.times(2)).receive(requests.capture());
        var records = requests.getAllValues();
        assertThat(records.get(0).events().getFirst().recordNo())
            .isEqualTo(records.get(1).events().getFirst().recordNo());
    }

    @Test
    void acknowledgesIgnoredSourcesWithoutCreatingPendingMessagesOrTelemetry() {
        when(gatewayMappingService.resolve(isNull(), eq("L001")))
            .thenThrow(new EquipmentGatewayMappingApplicationService.IgnoredEquipmentGatewayMappingException("已忽略"));
        assertThat(service.receive(payload("WORK", "42.5"))).isEqualTo(new EquipmentTelemetryResponse(0, 0));
        verify(telemetryApplicationService, never()).receive(any());
        verify(unmatchedMessageApplicationService, never()).record(any(), any(), any(), any());
    }

    @Test
    void convertsLegacyWorkSnapshotAndBindsConfiguredTenant() {
        when(telemetryApplicationService.receive(any())).thenAnswer(invocation -> {
            assertThat(TenantContext.tenantCode()).isEqualTo("btf-customer");
            assertThat(TenantContext.actor()).isEqualTo("btf-hessian.monitor");
            return new EquipmentTelemetryResponse(1, 0);
        });

        var result = service.receive(payload("WORK", "42.5"));

        assertThat(result).isEqualTo(new EquipmentTelemetryResponse(1, 0));
        ArgumentCaptor<EquipmentTelemetryRequest> request = ArgumentCaptor.forClass(EquipmentTelemetryRequest.class);
        verify(telemetryApplicationService).receive(request.capture());
        var event = request.getValue().events().getFirst();
        assertThat(event.username()).isNull();
        assertThat(event.machineName()).isEqualTo("L001");
        assertThat(event.status()).isEqualTo("PROCESSING");
        assertThat(event.eventType()).isEqualTo("LEGACY_MONITOR_SNAPSHOT");
        assertThat(event.recordNo()).startsWith("LEGACY:").hasSize(71);
        assertThat(event.machiningCount()).isEqualTo(120);
        assertThat(event.operatorName()).isEqualTo("操作员甲");
        assertThat(event.programNo()).isEqualTo("JOB-A");
        assertThat(event.progressPercent()).isEqualByComparingTo("42.5");
        assertThat(event.spindleStatus()).isEqualTo("1110");
        assertThat(event.attributes()).containsEntry("legacyState", "WORK")
            .containsEntry("runtimeSeconds", new BigDecimal("3600"))
            .containsEntry("routeLength", new BigDecimal("5000"));
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keepsWaitAsIdleAndRetainsTheOriginalState() {
        when(telemetryApplicationService.receive(any())).thenReturn(new EquipmentTelemetryResponse(1, 0));

        service.receive(payload("WAIT", "0"));

        ArgumentCaptor<EquipmentTelemetryRequest> request = ArgumentCaptor.forClass(EquipmentTelemetryRequest.class);
        verify(telemetryApplicationService).receive(request.capture());
        var event = request.getValue().events().getFirst();
        assertThat(event.status()).isEqualTo("IDLE");
        assertThat(event.attributes()).containsEntry("legacyState", "WAIT");
    }

    @Test
    void rejectsMalformedHeadersAndOutOfRangeProgressBeforeWritingTelemetry() {
        assertThatThrownBy(() -> service.receive("[{\"type\":\"wrong\"}]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RealtimeInf");
        assertThatThrownBy(() -> service.receive(payload("WORK", "100.1")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("finper");
    }

    @Test
    void skipsAnUnmappedSnapshotWithoutBlockingMappedSnapshotsInTheSameBatch() {
        when(gatewayMappingService.resolve(isNull(), eq("UNMAPPED-01")))
            .thenThrow(new EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException("未映射"));
        when(telemetryApplicationService.receive(any())).thenReturn(new EquipmentTelemetryResponse(1, 0));

        var result = service.receive("""
            [
              {"type":"RealtimeInf"},
              {"machinename":"UNMAPPED-01","state":"OFFLINE","updateCurTime":"2026-09-22 09:30:00","finHole":"0","finper":"0"},
              {"machinename":"L001","state":"WORK","updateCurTime":"2026-09-22 09:30:00","finHole":"120","finper":"42.5"}
            ]
            """);

        assertThat(result).isEqualTo(new EquipmentTelemetryResponse(1, 0));
        ArgumentCaptor<EquipmentTelemetryRequest> request = ArgumentCaptor.forClass(EquipmentTelemetryRequest.class);
        verify(telemetryApplicationService).receive(request.capture());
        assertThat(request.getValue().events()).extracting(event -> event.machineName()).containsExactly("L001");
    }

    @Test
    void acknowledgesAnAllUnmappedBatchWithoutSendingItToTelemetryStorage() {
        when(gatewayMappingService.resolve(isNull(), eq("UNMAPPED-01")))
            .thenThrow(new EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException("未映射"));

        var result = service.receive("""
            [
              {"type":"RealtimeInf"},
              {"machinename":"UNMAPPED-01","state":"OFFLINE","updateCurTime":"2026-09-22 09:30:00","finHole":"0","finper":"0"}
            ]
            """);

        assertThat(result).isEqualTo(new EquipmentTelemetryResponse(0, 0));
        verify(telemetryApplicationService, never()).receive(any());
    }

    @Test
    void processesValidDevicesBeforeAndAfterABrokenMappingAndRecordsTheIssue() throws Exception {
        when(gatewayMappingService.resolve(isNull(), eq("CNC-15"))).thenReturn(null);
        when(gatewayMappingService.resolve(isNull(), eq("CNC-17"))).thenReturn(null);
        when(gatewayMappingService.resolve(isNull(), eq("OLD-PC")))
            .thenThrow(new EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException("映射失效：目标设备不存在"));
        when(gatewayMappingService.resolve(isNull(), eq("EQP001")))
            .thenThrow(new EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException("未配置映射"));
        when(telemetryApplicationService.receive(any())).thenReturn(new EquipmentTelemetryResponse(2, 0));

        var result = service.receive("""
            [
              {"type":"RealtimeInf"},
              {"machinename":"CNC-15","state":"WORK","updateCurTime":"2026-10-09 15:30:00","finHole":"10","finper":"20"},
              {"machinename":"OLD-PC","state":"IDLE","updateCurTime":"2026-10-09 15:30:00","finHole":"0","finper":"0"},
              {"machinename":"EQP001","state":"IDLE","updateCurTime":"2026-10-09 15:30:00","finHole":"0","finper":"0"},
              {"machinename":"CNC-17","state":"WORK","updateCurTime":"2026-10-09 15:30:00","finHole":"20","finper":"40"}
            ]
            """);

        assertThat(result).isEqualTo(new EquipmentTelemetryResponse(2, 0));
        var request = ArgumentCaptor.forClass(EquipmentTelemetryRequest.class);
        verify(telemetryApplicationService).receive(request.capture());
        assertThat(request.getValue().events()).extracting(event -> event.machineName()).containsExactly("CNC-15", "CNC-17");
        var rawPayload = ArgumentCaptor.forClass(String.class);
        verify(unmatchedMessageApplicationService).record(isNull(), eq("OLD-PC"), any(), rawPayload.capture());
        assertThat(new ObjectMapper().readTree(rawPayload.getValue()).path("mappingIssue").asText()).contains("目标设备不存在");
        verify(unmatchedMessageApplicationService).record(isNull(), eq("EQP001"), any(), any());
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doesNotHideUnexpectedMappingFailures() {
        when(gatewayMappingService.resolve(isNull(), eq("L001"))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.receive(payload("WORK", "42.5")))
            .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
        verify(telemetryApplicationService, never()).receive(any());
        verify(unmatchedMessageApplicationService, never()).record(any(), any(), any(), any());
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    private String payload(String state, String percent) {
        return """
            [
              {"type":"RealtimeInf"},
              {
                "id":"1",
                "machinename":"L001",
                "machinetype":"0",
                "spindlestatus":"1110",
                "state":"%s",
                "updateCurTime":"2026-09-22 09:30:00",
                "runtime":"3600",
                "username":"操作员甲",
                "programname":"JOB-A",
                "finHole":"120",
                "routLen":"5000",
                "finper":"%s",
                "screentext":""
              }
            ]
            """.formatted(state, percent);
    }
}
