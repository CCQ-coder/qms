package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record CreateEquipmentRequest(
    @NotBlank(message = "不能为空") String assetNo,
    @NotBlank(message = "不能为空") String name,
    @NotBlank(message = "不能为空") String equipmentType,
    String manufacturer,
    String model,
    String processCode,
    String processName,
    String responsibleName,
    LocalDateTime calibrationDueAt
) {
}
