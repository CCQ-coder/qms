package com.pcbqms.equipment.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** The normalized contract implemented by every equipment gateway. */
public record EquipmentTelemetryEventRequest(
    String username,
    String machineName,
    @NotNull(message = "不能为空") LocalDateTime deviceTime,
    @NotBlank(message = "不能为空") String eventType,
    String status,
    String recordNo,
    Integer machiningCount,
    String programNo,
    String programVersion,
    String materialNo,
    String workOrderNo,
    String batchNo,
    String operatorName,
    @DecimalMin(value = "0", message = "不能小于 0") @DecimalMax(value = "100", message = "不能大于 100") BigDecimal progressPercent,
    String spindleStatus,
    String alarmCode,
    String alarmMessage,
    Map<String, Object> attributes
) {
}
