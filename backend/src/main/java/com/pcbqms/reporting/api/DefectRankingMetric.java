package com.pcbqms.reporting.api;

/** Aggregated defect data in the caller's authorized process scope. */
public record DefectRankingMetric(String defectSummary, long failedRecords, long defectiveQuantity) {
}
