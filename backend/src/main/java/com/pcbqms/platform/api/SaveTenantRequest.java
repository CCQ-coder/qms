package com.pcbqms.platform.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record SaveTenantRequest(
    @NotBlank(message = "租户编码不能为空") String code,
    @NotBlank(message = "租户名称不能为空") String name,
    @NotBlank(message = "管理员账号不能为空") String adminUserName,
    String adminDisplayName,
    String adminPassword,
    String status,
    List<String> menuCodes
) {
}
