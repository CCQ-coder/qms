package com.pcbqms.workorder.api;

import java.util.List;

/** A server-side page of read-only production-instruction records. */
public record WorkOrderPageResponse(
    List<WorkOrderResponse> records,
    long total,
    long page,
    long pageSize
) {
}
