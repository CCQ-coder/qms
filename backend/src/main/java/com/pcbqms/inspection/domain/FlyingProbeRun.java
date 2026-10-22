package com.pcbqms.inspection.domain;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FlyingProbeRun {
    private String runKey;
    private String tenantCode;
    private String equipmentId;
    private String sourceFile;
    private LocalDateTime sourceStartTime;
    private String materialNo;
    private String workOrderNo;
    private LocalDateTime lastDeviceTime;
    private Integer testedCount;
    private Integer goodCount;
    private Integer badCount;
    private String status;
    private String reason;
    private LocalDateTime updatedAt;
}
