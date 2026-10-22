package com.pcbqms.inspection.api;

import java.util.List;

public record FinishedTestRecordPage(List<FinishedTestInspectionRecordResponse> items, String nextCursor) { }
