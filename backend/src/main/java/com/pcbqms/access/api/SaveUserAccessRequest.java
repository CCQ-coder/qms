package com.pcbqms.access.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record SaveUserAccessRequest(
    @NotBlank(message = "不能为空") String userName,
    String displayName,
    @NotBlank(message = "不能为空") String roleCode,
    String status,
    String password,
    List<String> processCodes
) {
}
