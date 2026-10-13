package com.pcbqms.access.api;

public record PasswordLoginResponse(
    String accessToken,
    long expiresIn,
    String tenantCode,
    String userName,
    String displayName,
    String roleCode
) {
}
