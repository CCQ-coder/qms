package com.pcbqms.integration.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.common.tenancy.FlyingProbeDeviceTenantContextFilter;
import com.pcbqms.common.tenancy.FlyingProbeDeviceTenantContextFilter.DeviceIdentity;
import com.pcbqms.equipment.api.EquipmentTelemetryResponse;
import com.pcbqms.integration.application.FlyingProbeDeviceUploadService;
import com.pcbqms.integration.application.GatewayHostStatusApplicationService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flying-probe")
@RequiredArgsConstructor
public class FlyingProbeDeviceUploadController {
    private final FlyingProbeDeviceUploadService uploadService;
    private final GatewayHostStatusApplicationService statusService;

    @PostMapping("/device-files")
    public ApiResponse<EquipmentTelemetryResponse> receiveFile(
            @RequestAttribute(FlyingProbeDeviceTenantContextFilter.IDENTITY_ATTRIBUTE) DeviceIdentity identity,
            @RequestBody FlyingProbeDeviceFileRequest request) {
        return ApiResponse.of(uploadService.receive(identity, request));
    }

    @PostMapping("/device-host-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveStatus(
            @RequestAttribute(FlyingProbeDeviceTenantContextFilter.IDENTITY_ATTRIBUTE) DeviceIdentity identity,
            @RequestBody DeviceHostStatusRequest request) {
        identity.requireDevice(request.deviceId());
        if (!Integer.valueOf(1).equals(request.schemaVersion())) {
            throw new IllegalArgumentException("不支持的设备状态版本");
        }
        statusService.record(new GatewayHostStatusRequest(identity.machineName(), request.sampledAt(), request.cpuPercent(),
            request.memoryTotalBytes(), request.memoryAvailableBytes(), request.systemDiskTotalBytes(),
            request.systemDiskFreeBytes(), request.uptimeSeconds()));
    }

    public record DeviceHostStatusRequest(Integer schemaVersion, String deviceId, OffsetDateTime sampledAt,
        BigDecimal cpuPercent, Long memoryTotalBytes, Long memoryAvailableBytes,
        Long systemDiskTotalBytes, Long systemDiskFreeBytes, Long uptimeSeconds) {
    }
}
