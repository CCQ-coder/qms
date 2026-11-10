package com.pcbqms.inspection.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateNonconformityDetailRequest(
    @NotNull @Min(1) Integer scrapQuantity,
    String scrapReason
) { }
