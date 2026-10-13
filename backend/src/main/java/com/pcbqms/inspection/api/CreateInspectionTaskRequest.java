package com.pcbqms.inspection.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record CreateInspectionTaskRequest(
    @NotBlank(message = "不能为空") String workOrderNo,
    String batchNo,
    @NotBlank(message = "不能为空") String processCode,
    @NotBlank(message = "不能为空") String processName,
    @NotBlank(message = "不能为空") String taskType,
    String templateId,
    String assigneeName,
    LocalDateTime dueAt
) {
}
