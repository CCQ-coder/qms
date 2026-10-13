package com.pcbqms.administration.api;

import com.pcbqms.administration.domain.AdministrationAuditLog;
import java.time.LocalDateTime;

public record AdministrationAuditLogResponse(
    String id,
    String moduleCode,
    String operation,
    String targetCode,
    String summary,
    String actor,
    LocalDateTime createdAt
) {
    public static AdministrationAuditLogResponse from(AdministrationAuditLog item) {
        return new AdministrationAuditLogResponse(item.getId(), item.getModuleCode(), item.getOperation(), item.getTargetCode(),
            item.getSummary(), item.getActor(), item.getCreatedAt());
    }
}
