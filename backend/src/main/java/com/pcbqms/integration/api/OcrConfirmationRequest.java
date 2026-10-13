package com.pcbqms.integration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record OcrConfirmationRequest(
    @NotBlank(message = "不能为空") String workOrderNo,
    @NotBlank(message = "不能为空") String productCode,
    String productName,
    String specification,
    @NotBlank(message = "不能为空") String currentProcessCode,
    @NotBlank(message = "不能为空") String currentProcessName,
    @NotNull(message = "不能为空") @PositiveOrZero(message = "不能小于 0") Integer plannedQuantity,
    String batchNo
) {
}

