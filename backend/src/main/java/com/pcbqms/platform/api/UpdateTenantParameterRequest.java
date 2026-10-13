package com.pcbqms.platform.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateTenantParameterRequest(@NotBlank(message = "不能为空") String value) {
}

