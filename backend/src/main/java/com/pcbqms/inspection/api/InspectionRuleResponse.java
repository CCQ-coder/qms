package com.pcbqms.inspection.api;

import com.pcbqms.inspection.domain.InspectionRule;

public record InspectionRuleResponse(String id, String name, String productCode, String processCode,
                                     String processName, String taskType, String templateId, Boolean enabled, Integer priority) {
    public static InspectionRuleResponse from(InspectionRule rule) {
        return new InspectionRuleResponse(rule.getId(), rule.getName(), rule.getProductCode(), rule.getProcessCode(),
            rule.getProcessName(), rule.getTaskType(), rule.getTemplateId(), rule.getEnabled(), rule.getPriority());
    }
}
