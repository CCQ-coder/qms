package com.pcbqms.equipment.domain;

import lombok.Data;

/** Result of converting a flying-probe telemetry snapshot into an FQC record. */
@Data
public class EquipmentTelemetryProjectionStatus {
    private String telemetryId;
    private String status;
    private String reason;
    private String inspectionRecordId;
    private Integer deltaTested;
}
