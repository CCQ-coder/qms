package com.pcbqms.access.api;

import jakarta.validation.constraints.NotBlank;

public record PasswordLoginRequest(
    @NotBlank(message = "租户编码不能为空") String tenantCode,
    @NotBlank(message = "用户名不能为空") String userName,
    @NotBlank(message = "密码不能为空") String password
) {
}
