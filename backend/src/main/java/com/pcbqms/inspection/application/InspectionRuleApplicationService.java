package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.api.CreateInspectionRuleRequest;
import com.pcbqms.inspection.api.InspectionRuleResponse;
import com.pcbqms.inspection.domain.InspectionRule;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRuleMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InspectionRuleApplicationService {
    private final InspectionRuleMapper inspectionRuleMapper;
    private final InspectionTemplateApplicationService inspectionTemplateApplicationService;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public List<InspectionRuleResponse> list() {
        return inspectionRuleMapper.selectList(Wrappers.lambdaQuery(InspectionRule.class)
                .eq(InspectionRule::getTenantCode, TenantContext.tenantCode())
                .orderByAsc(InspectionRule::getPriority))
            .stream().map(InspectionRuleResponse::from).toList();
    }

    public InspectionRuleResponse create(CreateInspectionRuleRequest request) {
        inspectionAccessScopeService.requireTenantAdministrator();
        inspectionTemplateApplicationService.assertActiveTemplateBelongsToTenant(request.templateId());
        LocalDateTime now = LocalDateTime.now();
        InspectionRule rule = new InspectionRule();
        rule.setId(UUID.randomUUID().toString());
        rule.setTenantCode(TenantContext.tenantCode());
        rule.setName(request.name().trim());
        rule.setProductCode(request.productCode());
        rule.setProcessCode(request.processCode().trim());
        rule.setProcessName(request.processName().trim());
        rule.setTaskType(request.taskType().trim().toUpperCase());
        rule.setTemplateId(request.templateId());
        rule.setEnabled(request.enabled());
        rule.setPriority(request.priority());
        rule.setCreatedBy(TenantContext.actor());
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        inspectionRuleMapper.insert(rule);
        return InspectionRuleResponse.from(rule);
    }
}
