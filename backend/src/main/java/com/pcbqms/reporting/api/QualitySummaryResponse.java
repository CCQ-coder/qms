package com.pcbqms.reporting.api;

import java.util.List;

public record QualitySummaryResponse(long totalRecords, long passRecords, long failRecords, double firstPassRate,
                                     List<ProcessQualityMetric> byProcess,
                                     List<DefectRankingMetric> defectRanking,
                                     List<QualityTrendMetric> dailyTrend) {
}
