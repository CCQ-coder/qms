package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.Nonconformity;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

public record NonconformityResponse(
    String id,
    String ncrNo,
    String workOrderNo,
    String batchNo,
    String status,
    String initiatedBy,
    LocalDateTime createdAt,
    LocalDate detectionDate,
    String shiftName,
    String processCode,
    String processName,
    String responsibleDepartment,
    String remark,
    String dispositionMethod,
    List<NonconformityDetailResponse> details
) {
    public static NonconformityResponse from(Nonconformity entity, String workOrderNo, String batchNo,
                                             List<NonconformityDetailResponse> details) {
        return new NonconformityResponse(entity.getId(), entity.getNcrNo(), workOrderNo, batchNo,
            entity.getStatus(), entity.getInitiatedBy(), entity.getCreatedAt(), entity.getDetectionDate(),
            entity.getShiftName(), entity.getProcessCode(), entity.getProcessName(), entity.getResponsibleDepartment(),
            entity.getRemark(), entity.getDispositionMethod(), details);
    }
}

