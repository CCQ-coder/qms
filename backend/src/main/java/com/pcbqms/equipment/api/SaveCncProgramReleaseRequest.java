package com.pcbqms.equipment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record SaveCncProgramReleaseRequest(
    @NotBlank(message = "不能为空") String equipmentId,
    @NotBlank(message = "不能为空") String barcode,
    @NotBlank(message = "不能为空") String programNo,
    String programName,
    @NotBlank(message = "不能为空") String programVersion,
    @NotBlank(message = "不能为空") String fileUrl,
    @NotBlank(message = "不能为空") @Pattern(regexp = "(?i)[0-9a-f]{64}", message = "必须是 64 位 SHA-256 校验值") String checksumSha256,
    @PositiveOrZero(message = "不能小于 0") Long fileSizeBytes,
    String status
) {
}
