package com.pcbqms.inspection.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateInspectionTemplateStatusRequest(@NotBlank(message = "不能为空") String status) {
}
