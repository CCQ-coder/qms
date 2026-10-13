package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.List;

public record CompleteMaintenanceRequest(
    @NotBlank(message = "不能为空") String result,
    List<@Valid CompleteMaintenanceItemRequest> items
) {
}
