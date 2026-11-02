package com.pcbqms.integration.api;

public record FlyingProbeDeviceFileRequest(Integer schemaVersion, String deviceId, String jobName,
    Long createdFileTime, Long modifiedFileTime, String sha256, String contentBase64) {
}
