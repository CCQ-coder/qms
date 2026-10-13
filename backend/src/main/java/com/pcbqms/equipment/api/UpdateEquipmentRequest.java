package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record UpdateEquipmentRequest(
    @NotBlank(message = "设备名称不能为空") String name,
    @NotBlank(message = "设备类型不能为空") String equipmentType,
    String manufacturer,
    String model,
    String processCode,
    String processName,
    String responsibleName,
    LocalDateTime calibrationDueAt
) {
}
