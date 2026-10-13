package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.InspectionRecord;
import java.time.LocalDateTime;

public record InspectionRecordResponse(
    String id,
    String taskId,
    String verdict,
    Integer defectiveQuantity,
    String defectSummary,
    String defectLocation,
    String ncrNo,
    LocalDateTime inspectedAt
) {
    public static InspectionRecordResponse from(InspectionRecord record, String ncrNo) {
        return new InspectionRecordResponse(
            record.getId(), record.getTaskId(), record.getVerdict(), record.getDefectiveQuantity(),
            record.getDefectSummary(), record.getDefectLocation(), ncrNo, record.getInspectedAt()
        );
    }
}
