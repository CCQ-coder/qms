package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record CreateMaintenanceRequest(
    @NotBlank(message = "不能为空") String equipmentId,
    @NotBlank(message = "不能为空") String maintenanceType,
    LocalDateTime scheduledAt,
    @NotBlank(message = "不能为空") String description,
    String executorName
) {
}

