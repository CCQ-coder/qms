package com.pcbqms.equipment.api;

import java.math.BigDecimal;

/** Vendor measurements retained as reported; board count semantics are not inferred. */
public record ZhongboTestReportFields(
    String portNumber,
    Long panelsTested,
    Long errorTested,
    Long openErrors,
    Long shortErrors,
    Long totalPoints,
    BigDecimal utilizationRate,
    BigDecimal utilizationDuration,
    Long counts,
    BigDecimal openThresh,
    BigDecimal shortThresh,
    Long hit
) {
}
