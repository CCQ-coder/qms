package com.pcbqms.inspection.api;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class FlyingProbeRetryableRecordResponse {
    private String id;
    private String equipmentCode;
    private String machineName;
    private String materialNo;
    private LocalDateTime deviceTime;
    private String sourceFile;
    private LocalDateTime sourceStartTime;
    private String reason;
    private String workOrderNo;
    private Integer cumulativeTested;
    private Integer cumulativeGood;
    private Integer cumulativeBad;
}
