package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;

public record CncFileRequest(
    String username,
    String machineName,
    @NotBlank(message = "不能为空") String barcode
) {
}
