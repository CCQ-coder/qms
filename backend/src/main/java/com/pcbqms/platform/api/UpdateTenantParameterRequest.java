package com.pcbqms.platform.api;

import jakarta.validation.constraints.NotNull;

public record UpdateTenantParameterRequest(@NotNull(message = "不能为空") String value) {
}

