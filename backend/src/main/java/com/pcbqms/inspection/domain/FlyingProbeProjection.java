package com.pcbqms.inspection.domain;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FlyingProbeProjection {
    private String telemetryId;
    private String tenantCode;
    private String equipmentId;
    private String machineName;
    private String materialNo;
    private LocalDateTime deviceTime;
    private LocalDateTime receivedAt;
    private String sourceRecordNo;
    private String sourceFile;
    private LocalDateTime sourceStartTime;
    private String runKey;
    private Integer cumulativeTested;
    private Integer cumulativeGood;
    private Integer cumulativeBad;
    private Integer deltaTested;
    private Integer deltaGood;
    private Integer deltaBad;
    private String workOrderNo;
    private String batchNo;
    private String inspectionTaskId;
    private String inspectionRecordId;
    private String status;
    private String reason;
    private String rawPayload;
    private LocalDateTime retryAfter;
    private LocalDateTime processedAt;
}
