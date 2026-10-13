package com.pcbqms.dashboard.api;

public record DashboardSummaryResponse(
    long activeWorkOrders,
    long pendingInspectionTasks,
    long completedInspectionTasks,
    long openNonconformities,
    double firstPassRate
) {
}

