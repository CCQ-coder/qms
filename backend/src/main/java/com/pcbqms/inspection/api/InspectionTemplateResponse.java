package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.InspectionTemplate;
import java.util.List;

public record InspectionTemplateResponse(
    String id,
    String templateCode,
    String name,
    String productCode,
    String processCode,
    String processName,
    String taskType,
    Integer versionNo,
    String status,
    List<InspectionTemplateItemResponse> items
) {
    public static InspectionTemplateResponse from(InspectionTemplate template, List<InspectionTemplateItemResponse> items) {
        return new InspectionTemplateResponse(template.getId(), template.getTemplateCode(), template.getName(),
            template.getProductCode(), template.getProcessCode(), template.getProcessName(), template.getTaskType(),
            template.getVersionNo(), template.getStatus(), items);
    }
}
