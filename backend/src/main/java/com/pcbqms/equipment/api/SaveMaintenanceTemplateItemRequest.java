package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record SaveMaintenanceTemplateItemRequest(
    @NotBlank(message = "设备类型不能为空") String equipmentType,
    @NotBlank(message = "维护类型不能为空") String maintenanceType,
    @NotBlank(message = "项目编码不能为空") String itemCode,
    @NotBlank(message = "项目名称不能为空") String itemName,
    BigDecimal lowerLimit,
    BigDecimal upperLimit,
    String unit
) {
}
