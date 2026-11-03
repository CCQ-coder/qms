package com.pcbqms.inspection.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BindFlyingProbeWorkOrderRequest(@NotBlank @Size(max = 80) String workOrderNo) { }
