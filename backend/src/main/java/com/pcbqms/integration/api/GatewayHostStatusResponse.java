package com.pcbqms.integration.api;

import java.math.BigDecimal;
import java.time.Instant;

public record GatewayHostStatusResponse(
    String hostName,
    Instant sampledAt,
    BigDecimal cpuPercent,
    long memoryTotalBytes,
    long memoryAvailableBytes,
    long systemDiskTotalBytes,
    long systemDiskFreeBytes,
    long uptimeSeconds,
    Instant receivedAt,
    String equipmentId,
    String equipmentCode,
    String equipmentName
) {
}
