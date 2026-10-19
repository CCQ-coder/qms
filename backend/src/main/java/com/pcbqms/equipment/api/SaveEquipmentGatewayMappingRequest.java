package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;

public record SaveEquipmentGatewayMappingRequest(
    @NotBlank(message = "不能为空") String equipmentId,
    String username,
    String machineName
) {
}
