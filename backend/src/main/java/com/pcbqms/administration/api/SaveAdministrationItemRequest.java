package com.pcbqms.administration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record SaveAdministrationItemRequest(
    @NotBlank(message = "编码不能为空") String itemCode,
    @NotBlank(message = "名称不能为空") @Size(max = 160, message = "名称不能超过160字") String itemName,
    String parentCode,
    String description,
    String status,
    Integer sortOrder,
    Map<String, String> attributes
) {
}
