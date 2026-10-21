package com.pcbqms.inspection.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateNonconformityDetailRequest(
    @NotBlank String workOrderNo,
    String unit,
    @NotNull @Min(1) Integer scrapQuantity,
    String scrapReason,
    String remark
) { }
