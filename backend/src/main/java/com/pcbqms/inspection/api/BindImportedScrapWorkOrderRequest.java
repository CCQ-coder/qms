package com.pcbqms.inspection.api;

import jakarta.validation.constraints.NotBlank;

public record BindImportedScrapWorkOrderRequest(@NotBlank String workOrderNo) { }
