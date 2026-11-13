package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.NonconformityDetail;
import com.pcbqms.workorder.domain.WorkOrder;
import java.math.BigDecimal;

public record NonconformityDetailResponse(
    String id,
    Integer lineNo,
    String workOrderNo,
    String internalProductNo,
    String materialNo,
    String workOrderInternalProductNo,
    Integer layerCount,
    String processName,
    String unit,
    BigDecimal singlePcsArea,
    Integer scrapQuantity,
    BigDecimal scrapArea,
    String scrapReason,
    String remark,
    String status
) {
    public static NonconformityDetailResponse from(NonconformityDetail detail, WorkOrder workOrder) {
        return new NonconformityDetailResponse(detail.getId(), detail.getLineNo(),
            workOrder == null ? (detail.getWorkOrderId() == null ? null : "已删除工单") : workOrder.getWorkOrderNo(),
            detail.getInternalProductNo() != null ? detail.getInternalProductNo()
                : workOrder == null ? null : workOrder.getInternalProductNo(),
            detail.getMaterialNo() != null ? detail.getMaterialNo()
                : workOrder == null ? null : workOrder.getMaterialNo(),
            workOrder == null ? null : workOrder.getInternalProductNo(),
            detail.getLayerCount() != null ? detail.getLayerCount()
                : workOrder == null ? null : workOrder.getLayerCount(),
            detail.getProcessName(), detail.getUnit(), detail.getSinglePcsArea(), detail.getScrapQuantity(),
            detail.getScrapArea(), detail.getScrapReason(), detail.getRemark(), detail.getStatus());
    }
}
