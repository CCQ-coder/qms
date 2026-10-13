package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CompleteMaintenanceItemRequest(
    @NotBlank(message = "维护项目不能为空") String maintenanceItemId,
    @NotNull(message = "实测值不能为空") BigDecimal measuredValue
) {
}
