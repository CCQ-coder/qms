package com.pcbqms.reporting.domain;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class FinishedTestFact {
    private LocalDateTime inspectedAt;
    private String workOrderNo;
    private String materialNo;
    private String equipmentAssetNo;
    private Integer defectiveQuantity;
    private String resultsJson;
}
