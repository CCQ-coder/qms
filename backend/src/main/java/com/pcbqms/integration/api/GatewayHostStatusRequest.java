package com.pcbqms.integration.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record GatewayHostStatusRequest(
    @NotBlank @Size(max = 160) String hostName,
    @NotNull OffsetDateTime sampledAt,
    @DecimalMin("0") @DecimalMax("100") BigDecimal cpuPercent,
    @NotNull @Positive Long memoryTotalBytes,
    @NotNull @PositiveOrZero Long memoryAvailableBytes,
    @NotNull @Positive Long systemDiskTotalBytes,
    @NotNull @PositiveOrZero Long systemDiskFreeBytes,
    @NotNull @PositiveOrZero Long uptimeSeconds
) {
}
