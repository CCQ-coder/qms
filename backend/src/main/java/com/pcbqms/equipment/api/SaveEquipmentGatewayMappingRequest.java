package com.pcbqms.equipment.api;


public record SaveEquipmentGatewayMappingRequest(
    String equipmentId,
    String username,
    String machineName,
    Boolean ignored
) {
    public SaveEquipmentGatewayMappingRequest {
        ignored = Boolean.TRUE.equals(ignored);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "接收数据时必须选择 QMS 设备")
    public boolean isTargetValid() {
        return Boolean.TRUE.equals(ignored) || org.springframework.util.StringUtils.hasText(equipmentId);
    }

    public SaveEquipmentGatewayMappingRequest(String equipmentId, String username, String machineName) {
        this(equipmentId, username, machineName, false);
    }
}
