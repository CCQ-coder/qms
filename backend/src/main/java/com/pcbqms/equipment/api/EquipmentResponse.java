package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.Equipment;
import java.time.LocalDateTime;

public record EquipmentResponse(
    String id,
    String assetNo,
    String name,
    String equipmentType,
    String manufacturer,
    String model,
    String processCode,
    String processName,
    String status,
    String responsibleName,
    LocalDateTime calibrationDueAt,
    String calibrationStatus
) {
    public static EquipmentResponse from(Equipment entity) {
        return new EquipmentResponse(entity.getId(), entity.getAssetNo(), entity.getName(), entity.getEquipmentType(),
            entity.getManufacturer(), entity.getModelName(), entity.getProcessCode(), entity.getProcessName(), entity.getStatus(), entity.getResponsibleName(), entity.getCalibrationDueAt(),
            calibrationStatus(entity.getCalibrationDueAt()));
    }

    private static String calibrationStatus(LocalDateTime calibrationDueAt) {
        if (calibrationDueAt == null) {
            return "NOT_SET";
        }
        LocalDateTime now = LocalDateTime.now();
        if (calibrationDueAt.isBefore(now)) {
            return "OVERDUE";
        }
        return calibrationDueAt.isAfter(now.plusDays(30)) ? "NORMAL" : "DUE_SOON";
    }
}
