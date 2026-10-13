package com.pcbqms.equipment.api;

import java.util.List;

public record EquipmentPageResponse(
    List<EquipmentResponse> records,
    long total,
    long page,
    long pageSize
) {
}
