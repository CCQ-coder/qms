package com.pcbqms.inspection.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 已完成制程检验单的展示模型，保留工单归属、检验结果及后续处置信息。 */
public record ProcessInspectionRecordResponse(
    String id,
    String taskNo,
    String processCode,
    String processName,
    String workOrderNo,
    String batchNo,
    String internalProductNo,
    String pcbPartNo,
    Integer inspectionQuantity,
    Integer goodQuantity,
    Integer defectiveQuantity,
    String templateName,
    Integer templateVersionNo,
    String inspectedBy,
    String verdict,
    String followUpStatus,
    String ncrNo,
    String defectSummary,
    String defectLocation,
    Map<String, Object> results,
    List<String> attachmentUrls,
    LocalDateTime inspectedAt
) {
}
