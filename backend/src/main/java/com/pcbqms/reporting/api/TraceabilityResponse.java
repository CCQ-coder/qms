package com.pcbqms.reporting.api;

import com.pcbqms.workorder.api.WorkOrderResponse;
import java.time.LocalDateTime;
import java.util.List;

public record TraceabilityResponse(
    WorkOrderResponse workOrder,
    String batchNo,
    List<TraceInspectionRecord> inspectionRecords,
    List<TraceNonconformity> nonconformities
) {
    public record TraceInspectionRecord(String taskNo, String processName, String taskType, String verdict,
                                        Integer defectiveQuantity, String defectSummary, String defectLocation, String inspectedBy,
                                        LocalDateTime inspectedAt) {
    }

    public record TraceNonconformity(String ncrNo, String status, String initiatedBy, LocalDateTime createdAt) {
    }
}
