package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.EquipmentType;

public record EquipmentTypeResponse(String code, String name) {
    public static EquipmentTypeResponse from(EquipmentType type) {
        return new EquipmentTypeResponse(type.name(), type.displayName());
    }
}
