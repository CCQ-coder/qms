package com.pcbqms.reporting.api;

/** Daily inspection quality metric in the caller's authorized scope. */
public record QualityTrendMetric(String inspectionDate, long totalRecords, long passRecords, long failRecords,
                                 double passRate) {
}
