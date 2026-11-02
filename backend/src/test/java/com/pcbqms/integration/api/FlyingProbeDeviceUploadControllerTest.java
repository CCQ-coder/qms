package com.pcbqms.integration.api;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.pcbqms.common.api.RestExceptionHandler;
import com.pcbqms.common.tenancy.FlyingProbeDeviceTenantContextFilter;
import com.pcbqms.common.tenancy.FlyingProbeDeviceTenantContextFilter.DeviceIdentity;
import com.pcbqms.integration.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.ArgumentCaptor;

class FlyingProbeDeviceUploadControllerTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;

    @Test void authenticatedJsonSavesRawFileAndAcknowledgesOnlyAfterTelemetry() throws Exception {
        var telemetry = mock(com.pcbqms.equipment.application.EquipmentTelemetryApplicationService.class);
        when(telemetry.receive(any())).thenReturn(new com.pcbqms.equipment.api.EquipmentTelemetryResponse(1, 0));
        var upload = new FlyingProbeDeviceUploadService(new FlyingProbeIniParser(),
            new com.pcbqms.integration.infrastructure.storage.FlyingProbeRawStorage(root.toString()), telemetry);
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<DeviceIdentity>>any(), any()))
            .thenReturn(java.util.List.of(new DeviceIdentity("customer-b", "飞针01")));
        var mvc = MockMvcBuilders.standaloneSetup(new FlyingProbeDeviceUploadController(upload,
                mock(GatewayHostStatusApplicationService.class)))
            .addFilters(new FlyingProbeDeviceTenantContextFilter(jdbc))
            .setControllerAdvice(new RestExceptionHandler()).build();
        String body = """
            {"schemaVersion":1,"deviceId":"飞针01","jobName":"任务一",
             "createdFileTime":134065221001234560,"modifiedFileTime":134065257009876544,
             "sha256":"e5c849d2995a76d6d17edf560edf59af644709660e2f825f2e10d0311e0a5f2e",
             "contentBase64":"VGVzdF9Db3VudGVycyAyIDIgMCAwMToyNzowOQ=="}
            """;
        mvc.perform(post("/api/v1/flying-probe/device-files").header("X-Device-Token", "A".repeat(43))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.acceptedCount").value(1));
        try (var paths = java.nio.file.Files.walk(root)) {
            assertThat(paths.filter(java.nio.file.Files::isRegularFile).toList()).hasSize(1);
        }
        mvc.perform(post("/api/v1/flying-probe/device-files").header("X-Device-Token", "A".repeat(43))
            .contentType(MediaType.APPLICATION_JSON).content(body.replace("飞针01", "OTHER")))
            .andExpect(status().isForbidden());
        verify(telemetry, times(1)).receive(any());
    }

    @Test void statusUsesBoundMachineRatherThanClaimedHostAndRejectsOtherDevice() throws Exception {
        var service = mock(FlyingProbeDeviceUploadService.class);
        var status = mock(GatewayHostStatusApplicationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new FlyingProbeDeviceUploadController(service, status))
            .setControllerAdvice(new RestExceptionHandler()).build();
        String body = """
            {"schemaVersion":1,"deviceId":"飞针01","hostName":"ATTACKER","sampledAt":"2026-11-02T01:00:00Z",
             "cpuPercent":12.5,"memoryTotalBytes":100,"memoryAvailableBytes":50,
             "systemDiskTotalBytes":100,"systemDiskFreeBytes":50,"uptimeSeconds":60}
            """;
        mvc.perform(post("/api/v1/flying-probe/device-host-status")
            .requestAttr(FlyingProbeDeviceTenantContextFilter.IDENTITY_ATTRIBUTE, new DeviceIdentity("customer-b", "飞针01"))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        var saved = ArgumentCaptor.forClass(GatewayHostStatusRequest.class);
        verify(status).record(saved.capture());
        assertThat(saved.getValue().hostName()).isEqualTo("飞针01");
        assertThat(saved.getValue().memoryAvailableBytes()).isEqualTo(50);
        mvc.perform(post("/api/v1/flying-probe/device-host-status")
            .requestAttr(FlyingProbeDeviceTenantContextFilter.IDENTITY_ATTRIBUTE, new DeviceIdentity("customer-b", "OTHER"))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoMoreInteractions(status);
    }
}
