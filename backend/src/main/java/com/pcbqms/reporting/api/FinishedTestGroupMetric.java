package com.pcbqms.reporting.api;

public record FinishedTestGroupMetric(String key, long recordCount, long totalBoards, long goodBoards,
                                      long badBoards, long unknownQuantityRecords, double passRate) {
}
