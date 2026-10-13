package com.pcbqms.administration.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record SaveAdministrationItemRequest(
    @NotBlank(message = "编码不能为空") String itemCode,
    @NotBlank(message = "名称不能为空") String itemName,
    String parentCode,
    String description,
    String status,
    Integer sortOrder,
    Map<String, String> attributes
) {
}
