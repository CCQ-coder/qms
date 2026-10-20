package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record FlyingProbePresenceRequest(
    @NotBlank String machineName,
    @NotNull OffsetDateTime observedAt,
    @NotBlank String networkState,
    @NotBlank String sourceState,
    OffsetDateTime lastTxtChangeAt
) {
}
