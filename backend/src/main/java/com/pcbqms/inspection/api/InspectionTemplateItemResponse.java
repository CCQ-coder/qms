package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.InspectionTemplateItem;
import java.math.BigDecimal;
import java.util.List;

public record InspectionTemplateItemResponse(
    String id,
    Integer sortOrder,
    String itemCode,
    String itemName,
    String inputType,
    Boolean required,
    BigDecimal lowerLimit,
    BigDecimal upperLimit,
    String unit,
    List<String> options,
    String helpText
) {
    public static InspectionTemplateItemResponse from(InspectionTemplateItem item, List<String> options) {
        return new InspectionTemplateItemResponse(item.getId(), item.getSortOrder(), item.getItemCode(), item.getItemName(),
            item.getInputType(), item.getRequired(), item.getLowerLimit(), item.getUpperLimit(), item.getUnit(), options,
            item.getHelpText());
    }
}
