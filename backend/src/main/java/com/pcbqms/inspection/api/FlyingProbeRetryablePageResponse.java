package com.pcbqms.inspection.api;

import java.util.List;

public record FlyingProbeRetryablePageResponse(
    List<FlyingProbeRetryableRecordResponse> records, long total, long page, long pageSize
) { }
