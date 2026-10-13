package com.pcbqms.inspection.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateInspectionTemplateRequest(
    @NotBlank(message = "不能为空") String templateCode,
    @NotBlank(message = "不能为空") String name,
    String productCode,
    @NotBlank(message = "不能为空") String processCode,
    @NotBlank(message = "不能为空") String processName,
    @NotBlank(message = "不能为空") String taskType,
    @NotEmpty(message = "至少配置一个检验项") List<@Valid InspectionTemplateItemRequest> items
) {
}
