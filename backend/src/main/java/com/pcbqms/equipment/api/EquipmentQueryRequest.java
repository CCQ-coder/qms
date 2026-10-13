package com.pcbqms.equipment.api;

/** Optional filters for the paged equipment ledger. */
public record EquipmentQueryRequest(
    String keyword,
    String equipmentType,
    String status,
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
