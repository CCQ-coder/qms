package com.pcbqms.integration.application;

import com.pcbqms.common.tenancy.FlyingProbeDeviceTenantContextFilter.DeviceIdentity;
import com.pcbqms.equipment.api.EquipmentTelemetryRequest;
import com.pcbqms.equipment.api.EquipmentTelemetryResponse;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import com.pcbqms.integration.api.FlyingProbeDeviceFileRequest;
import com.pcbqms.integration.infrastructure.storage.FlyingProbeRawStorage;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FlyingProbeDeviceUploadService {
    private static final int MAX_FILE_BYTES = 8_388_608;
    private final FlyingProbeIniParser parser;
    private final FlyingProbeRawStorage storage;
    private final EquipmentTelemetryApplicationService telemetry;

    public EquipmentTelemetryResponse receive(DeviceIdentity identity, FlyingProbeDeviceFileRequest request) {
        identity.requireDevice(request.deviceId());
        if (!Integer.valueOf(1).equals(request.schemaVersion())
            || request.deviceId() == null || request.deviceId().length() > 160
            || !validJob(request.jobName()) || request.createdFileTime() == null || request.modifiedFileTime() == null
            || request.sha256() == null || !request.sha256().matches("[a-fA-F0-9]{64}")
            || request.contentBase64() == null || request.contentBase64().length() > ((MAX_FILE_BYTES + 2) / 3) * 4) {
            throw new IllegalArgumentException("无效的飞针文件上传参数");
        }
        byte[] content = Base64.getDecoder().decode(request.contentBase64());
        if (content.length == 0 || content.length > MAX_FILE_BYTES
            || !FlyingProbeIniParser.sha256(content).equalsIgnoreCase(request.sha256())) {
            throw new IllegalArgumentException("飞针文件大小或 SHA-256 不匹配");
        }
        var event = parser.parse(request.deviceId(), request.jobName(), request.createdFileTime(), request.modifiedFileTime(), content);
        storage.store(identity.tenantCode(), content);
        return telemetry.receive(new EquipmentTelemetryRequest(List.of(event)));
    }

    private boolean validJob(String job) {
        return job != null && !job.isBlank() && job.length() <= 160 && !job.equals(".") && !job.equals("..")
            && job.codePoints().noneMatch(c -> c < 32 || "<>:\"/\\\\|?*".indexOf(c) >= 0);
    }
}
