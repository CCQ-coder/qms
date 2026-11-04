package com.pcbqms.reporting.api;

import java.util.List;

public record FinishedTestAnalysisResponse(long recordCount, long totalBoards, long goodBoards, long badBoards,
                                           long unknownQuantityRecords, double passRate,
                                           List<FinishedTestGroupMetric> byDate,
                                           List<FinishedTestGroupMetric> byWorkOrder,
                                           List<FinishedTestGroupMetric> byMaterial,
                                           List<FinishedTestGroupMetric> byEquipment) {
}
