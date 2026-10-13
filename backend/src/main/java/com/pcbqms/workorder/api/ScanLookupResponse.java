package com.pcbqms.workorder.api;

import java.util.List;

public record ScanLookupResponse(
    WorkOrderResponse workOrder,
    List<String> availableInspectionTaskNos
) {
}

