package com.pcbqms.inspection.api;

import jakarta.validation.constraints.NotBlank;

public record CloseCapaRequest(
    @NotBlank(message = "不能为空") String verificationResult,
    String recognitionNotes
) {
}
