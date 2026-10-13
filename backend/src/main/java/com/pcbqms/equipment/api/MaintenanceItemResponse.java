package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.EquipmentMaintenanceItem;
import java.math.BigDecimal;

public record MaintenanceItemResponse(
    String id,
    String itemCode,
    String itemName,
    BigDecimal lowerLimit,
    BigDecimal upperLimit,
    String unit,
    BigDecimal measuredValue,
    String judgement
) {
    public static MaintenanceItemResponse from(EquipmentMaintenanceItem item) {
        return new MaintenanceItemResponse(item.getId(), item.getItemCode(), item.getItemName(), item.getLowerLimit(),
            item.getUpperLimit(), item.getUnit(), item.getMeasuredValue(), item.getJudgement());
    }
}
