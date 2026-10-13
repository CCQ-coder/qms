package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.InspectionTask;
import java.time.LocalDateTime;

public record InspectionTaskResponse(
    String id,
    String taskNo,
    String workOrderNo,
    String batchNo,
    String processCode,
    String processName,
    String taskType,
    String templateId,
    String sourceType,
    String status,
    String assigneeName,
    LocalDateTime dueAt
) {
    public static InspectionTaskResponse from(InspectionTask task, String workOrderNo, String batchNo) {
        return new InspectionTaskResponse(
            task.getId(), task.getTaskNo(), workOrderNo, batchNo, task.getProcessCode(), task.getProcessName(),
            task.getTaskType(), task.getTemplateId(), task.getSourceType(), task.getStatus(), task.getAssigneeName(), task.getDueAt()
        );
    }
}
