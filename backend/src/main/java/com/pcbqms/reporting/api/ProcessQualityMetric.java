package com.pcbqms.reporting.api;

public record ProcessQualityMetric(String processCode, String processName, long totalRecords, long passRecords, long failRecords, double passRate) {
}

