package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.NonconformityDetail;
import java.math.BigDecimal;

public record NonconformityDetailResponse(
    String id,
    Integer lineNo,
    String workOrderNo,
    String internalProductNo,
    String processName,
    String unit,
    BigDecimal singlePcsArea,
    Integer scrapQuantity,
    BigDecimal scrapArea,
    String scrapReason,
    String remark,
    String status
) {
    public static NonconformityDetailResponse from(NonconformityDetail detail, String workOrderNo, String internalProductNo) {
        return new NonconformityDetailResponse(detail.getId(), detail.getLineNo(), workOrderNo, internalProductNo,
            detail.getProcessName(), detail.getUnit(), detail.getSinglePcsArea(), detail.getScrapQuantity(),
            detail.getScrapArea(), detail.getScrapReason(), detail.getRemark(), detail.getStatus());
    }
}
