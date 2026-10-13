package com.pcbqms.administration.api;

import com.pcbqms.administration.domain.AdministrationItem;
import java.time.LocalDateTime;
import java.util.Map;

public record AdministrationItemResponse(
    String id,
    String catalogType,
    String itemCode,
    String itemName,
    String parentCode,
    String description,
    String status,
    Integer sortOrder,
    Map<String, String> attributes,
    String createdBy,
    String updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static AdministrationItemResponse from(AdministrationItem item, Map<String, String> attributes) {
        return new AdministrationItemResponse(item.getId(), item.getCatalogType(), item.getItemCode(), item.getItemName(),
            item.getParentCode(), item.getDescription(), item.getStatus(), item.getSortOrder(), attributes,
            item.getCreatedBy(), item.getUpdatedBy(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
