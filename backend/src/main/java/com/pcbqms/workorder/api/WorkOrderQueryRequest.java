package com.pcbqms.workorder.api;

import java.time.LocalDate;

/** Optional filters accepted by the production-instruction search endpoint. */
public record WorkOrderQueryRequest(
    String keyword,
    String batchNo,
    String customerCode,
    String currentProcess,
    String status,
    String qualityStatus,
    String warehouseStatus,
    String priority,
    LocalDate inputDateFrom,
    LocalDate inputDateTo,
    LocalDate requiredDeliveryDateFrom,
    LocalDate requiredDeliveryDateTo,
    Long page,
    Long pageSize
) {
    public long resolvedPage() {
        return page == null ? 1 : Math.max(1, page);
    }

    public long resolvedPageSize() {
        return pageSize == null ? 20 : Math.min(100, Math.max(10, pageSize));
    }
}
