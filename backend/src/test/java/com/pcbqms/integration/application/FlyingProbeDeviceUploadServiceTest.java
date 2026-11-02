package com.pcbqms.integration.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.pcbqms.common.tenancy.FlyingProbeDeviceTenantContextFilter.DeviceIdentity;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import com.pcbqms.equipment.api.EquipmentTelemetryRequest;
import com.pcbqms.equipment.api.EquipmentTelemetryResponse;
import com.pcbqms.integration.api.FlyingProbeDeviceFileRequest;
import com.pcbqms.integration.infrastructure.storage.FlyingProbeRawStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlyingProbeDeviceUploadServiceTest {
    @TempDir Path root;
    EquipmentTelemetryApplicationService telemetry = mock(EquipmentTelemetryApplicationService.class);
    DeviceIdentity identity = new DeviceIdentity("customer-b", "飞针01");
    byte[] content = "Test_Counters 2 2 0 01:27:09".getBytes(StandardCharsets.UTF_8);
    FlyingProbeDeviceUploadService service() {
        return new FlyingProbeDeviceUploadService(new FlyingProbeIniParser(), new FlyingProbeRawStorage(root.toString()), telemetry);
    }
    @Test void savesOriginalBytesBeforeTelemetryAndReturnsDuplicateAcknowledgementOnRetry() throws Exception {
        when(telemetry.receive(any())).thenAnswer(call -> {
            try (var paths = Files.walk(root)) {
                assertThat(paths.filter(Files::isRegularFile).map(p -> {
                    try { return Files.readAllBytes(p); } catch (Exception e) { throw new RuntimeException(e); }
                }).toList()).singleElement().satisfies(bytes -> assertThat(bytes).isEqualTo(content));
            }
            EquipmentTelemetryRequest request = call.getArgument(0);
            assertThat(request.events().getFirst().attributes()).containsEntry("sourceFile", "飞针01/任务一.txt");
            return new EquipmentTelemetryResponse(0, 1);
        });
        var request = request("飞针01", "任务一", FlyingProbeIniParser.sha256(content), Base64.getEncoder().encodeToString(content));
        assertThat(service().receive(identity, request).duplicateCount()).isEqualTo(1);
        assertThat(service().receive(identity, request).duplicateCount()).isEqualTo(1);
    }
    @Test void rejectsForgedDeviceHashBase64PathAndOversizeWithoutPersisting() throws Exception {
        for (var request : new FlyingProbeDeviceFileRequest[] {
            request("OTHER", "任务一", FlyingProbeIniParser.sha256(content), Base64.getEncoder().encodeToString(content)),
            request("飞针01", "../任务一", FlyingProbeIniParser.sha256(content), Base64.getEncoder().encodeToString(content)),
            request("飞针01", "任务一", "0".repeat(64), Base64.getEncoder().encodeToString(content)),
            request("飞针01", "任务一", FlyingProbeIniParser.sha256(content), "!"),
            request("飞针01", "任务一", "0".repeat(64), Base64.getEncoder().encodeToString(new byte[8_388_609]))
        }) assertThatThrownBy(() -> service().receive(identity, request)).isInstanceOf(RuntimeException.class);
        try(var paths = Files.walk(root)) { assertThat(paths.filter(Files::isRegularFile).toList()).isEmpty(); }
        verifyNoInteractions(telemetry);
    }
    @Test void rejectsOneExtraDecodedByteEvenWhenItFitsTheSameBase64Length() {
        byte[] oversized = new byte[8_388_609];
        java.util.Arrays.fill(oversized, (byte) ' ');
        byte[] header = "Test_Counters 2 2 0 01:27:09\n".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(header, 0, oversized, 0, header.length);
        assertThatThrownBy(() -> service().receive(identity, request("飞针01", "任务一",
            FlyingProbeIniParser.sha256(oversized), Base64.getEncoder().encodeToString(oversized))))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(telemetry);
    }

    @Test void rawStorageFailureNeverAcknowledgesOrWritesTelemetry() throws Exception {
        Files.writeString(root.resolve("flying-probe"), "not a directory");
        assertThatThrownBy(() -> service().receive(identity, request("飞针01", "任务一",
            FlyingProbeIniParser.sha256(content), Base64.getEncoder().encodeToString(content))))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(telemetry);
    }
    private FlyingProbeDeviceFileRequest request(String device, String job, String hash, String base64) {
        return new FlyingProbeDeviceFileRequest(1, device, job, 134065221001234560L, 134065257009876544L, hash, base64);
    }
}
