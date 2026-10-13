package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.Nonconformity;
import java.time.LocalDateTime;

public record NonconformityResponse(
    String id,
    String ncrNo,
    String workOrderNo,
    String batchNo,
    String status,
    String initiatedBy,
    LocalDateTime createdAt
) {
    public static NonconformityResponse from(Nonconformity entity, String workOrderNo, String batchNo) {
        return new NonconformityResponse(entity.getId(), entity.getNcrNo(), workOrderNo, batchNo,
            entity.getStatus(), entity.getInitiatedBy(), entity.getCreatedAt());
    }
}

