package com.pcbqms.inspection.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CreateManualNonconformityRequest(
    @NotNull LocalDate detectionDate,
    String shiftName,
    @NotBlank String processCode,
    @NotBlank String processName,
    String responsibleDepartment,
    String remark,
    String dispositionMethod,
    String batchNo,
    @NotEmpty List<@Valid CreateNonconformityDetailRequest> details
) { }
