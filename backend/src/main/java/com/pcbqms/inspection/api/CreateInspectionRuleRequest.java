package com.pcbqms.inspection.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateInspectionRuleRequest(
    @NotBlank(message = "不能为空") String name,
    String productCode,
    @NotBlank(message = "不能为空") String processCode,
    @NotBlank(message = "不能为空") String processName,
    @NotBlank(message = "不能为空") String taskType,
    String templateId,
    @NotNull(message = "不能为空") Boolean enabled,
    @NotNull(message = "不能为空") @PositiveOrZero(message = "不能小于 0") Integer priority
) {
}
