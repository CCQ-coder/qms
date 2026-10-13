package com.pcbqms.masterdata.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateProcessRequest(
    @NotBlank(message = "不能为空") String processCode,
    @NotBlank(message = "不能为空") String processName,
    @NotNull(message = "不能为空") @PositiveOrZero(message = "不能小于 0") Integer sequenceNo
) {
}
