package com.pcbqms.inspection.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateNonconformityDetailRequest(
    String workOrderNo,
    String unit,
    @NotNull @Min(1) Integer scrapQuantity,
    String scrapReason,
    String remark,
    @Size(max = 160) String internalProductNo,
    @Size(max = 160) String materialNo,
    @Min(1) Integer layerCount,
    @DecimalMin("0") BigDecimal singlePcsArea,
    @DecimalMin("0") BigDecimal scrapArea
) { }
