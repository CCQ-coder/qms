package com.pcbqms.masterdata.api;

import jakarta.validation.constraints.Size;

public record UpdateProcessDepartmentRequest(@Size(max = 160) String responsibleDepartment) { }
