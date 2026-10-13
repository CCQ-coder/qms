package com.pcbqms.equipment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EquipmentTelemetryRequest(
    @NotEmpty(message = "至少提交一条遥测数据") @Size(max = 500, message = "单次最多提交 500 条遥测数据")
    List<@Valid EquipmentTelemetryEventRequest> events
) {
}
