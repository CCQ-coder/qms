package com.pcbqms.inspection.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateInspectionRecordRequest(
    @NotBlank(message = "不能为空") String verdict,
    @NotNull(message = "不能为空") @PositiveOrZero(message = "不能小于 0") Integer defectiveQuantity,
    String defectSummary,
    String defectLocation,
    Map<String, Object> results,
    java.util.List<String> attachmentUrls,
    @Size(max = 80, message = "不能超过 80 个字符") String equipmentAssetNo,
    @Size(max = 80, message = "不能超过 80 个字符") String clientRequestId
) {
}
