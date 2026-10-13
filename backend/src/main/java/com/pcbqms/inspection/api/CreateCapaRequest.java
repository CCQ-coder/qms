package com.pcbqms.inspection.api;

import jakarta.validation.constraints.NotBlank;

public record CreateCapaRequest(
    @NotBlank(message = "不能为空") String problemDescription,
    String teamMembers,
    String containmentAction,
    String rootCause,
    String correctiveAction,
    String preventiveAction
) {
}
