package com.pcbqms.masterdata.api;

import jakarta.validation.constraints.NotBlank;

public record CreateProductRequest(
    @NotBlank(message = "不能为空") String productCode,
    @NotBlank(message = "不能为空") String productName,
    String specification
) {
}
