package com.pcbqms.inspection.api;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A submitted FQC record enriched with the task, production and workstation context
 * required by the finished-test device record screen.
 */
public record FinishedTestInspectionRecordResponse(
    String id,
    String taskNo,
    LocalDateTime inspectedAt,
    String equipmentAssetNo,
    String workOrderNo,
    String batchNo,
    String internalProductNo,
    String productCode,
    Integer inspectionQuantity,
    Integer goodQuantity,
    Integer defectiveQuantity,
    String verdict,
    String defectSummary,
    String defectLocation,
    String inspectedBy,
    Map<String, Object> results
) { }
