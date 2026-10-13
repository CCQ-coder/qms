package com.pcbqms.platform.api;

import java.time.LocalDateTime;
import java.util.List;

public record TenantManagementResponse(
    String id,
    String code,
    String name,
    String status,
    String adminUserName,
    String adminDisplayName,
    boolean loginPinned,
    List<String> activeMenuCodes,
    LocalDateTime createdAt
) {
}
