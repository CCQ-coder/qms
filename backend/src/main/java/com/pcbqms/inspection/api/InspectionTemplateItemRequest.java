package com.pcbqms.inspection.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record InspectionTemplateItemRequest(
    @NotNull(message = "不能为空") @PositiveOrZero(message = "不能小于 0") Integer sortOrder,
    @NotBlank(message = "不能为空") String itemCode,
    @NotBlank(message = "不能为空") String itemName,
    @NotBlank(message = "不能为空") String inputType,
    @NotNull(message = "不能为空") Boolean required,
    BigDecimal lowerLimit,
    BigDecimal upperLimit,
    String unit,
    List<String> options,
    String helpText
) {
}
