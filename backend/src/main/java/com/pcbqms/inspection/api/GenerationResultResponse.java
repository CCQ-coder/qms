package com.pcbqms.inspection.api;

import java.util.List;

public record GenerationResultResponse(int createdCount, int skippedCount, List<InspectionTaskResponse> tasks) {
}

