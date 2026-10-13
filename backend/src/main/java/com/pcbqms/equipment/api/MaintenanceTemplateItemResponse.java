package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.EquipmentMaintenanceTemplateItem;
import java.math.BigDecimal;

public record MaintenanceTemplateItemResponse(
    String id,
    String equipmentType,
    String maintenanceType,
    String itemCode,
    String itemName,
    BigDecimal lowerLimit,
    BigDecimal upperLimit,
    String unit
) {
    public static MaintenanceTemplateItemResponse from(EquipmentMaintenanceTemplateItem item) {
        return new MaintenanceTemplateItemResponse(item.getId(), item.getEquipmentType(), item.getMaintenanceType(),
            item.getItemCode(), item.getItemName(), item.getLowerLimit(), item.getUpperLimit(), item.getUnit());
    }
}
