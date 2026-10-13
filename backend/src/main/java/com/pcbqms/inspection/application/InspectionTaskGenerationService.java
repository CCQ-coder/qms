package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.api.GenerationResultResponse;
import com.pcbqms.inspection.api.InspectionTaskResponse;
import com.pcbqms.inspection.domain.InspectionRule;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRuleMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.workorder.domain.ProductionBatch;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InspectionTaskGenerationService {
    private final WorkOrderMapper workOrderMapper;
    private final ProductionBatchMapper productionBatchMapper;
    private final InspectionRuleMapper inspectionRuleMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final InspectionTemplateApplicationService inspectionTemplateApplicationService;

    @Transactional
    public GenerationResultResponse generateForWorkOrder(String workOrderNo, String batchNo) {
        String tenantCode = TenantContext.tenantCode();
        WorkOrder workOrder = workOrderMapper.selectOne(Wrappers.lambdaQuery(WorkOrder.class)
            .eq(WorkOrder::getTenantCode, tenantCode).eq(WorkOrder::getWorkOrderNo, workOrderNo));
        if (workOrder == null) {
            throw new IllegalArgumentException("工单不存在：" + workOrderNo);
        }
        List<ProductionBatch> batches = resolveBatches(workOrder, batchNo);
        List<InspectionRule> rules = inspectionRuleMapper.selectList(Wrappers.lambdaQuery(InspectionRule.class)
            .eq(InspectionRule::getTenantCode, tenantCode)
            .eq(InspectionRule::getEnabled, true)
            .eq(InspectionRule::getProcessCode, workOrder.getCurrentProcessCode())
            .and(wrapper -> wrapper.isNull(InspectionRule::getProductCode).or().eq(InspectionRule::getProductCode, workOrder.getProductCode()))
            .orderByAsc(InspectionRule::getPriority));
        List<InspectionTaskResponse> generatedTasks = new ArrayList<>();
        int skipped = 0;
        for (ProductionBatch batch : batches) {
            for (InspectionRule rule : rules) {
                if (!inspectionTemplateApplicationService.isActiveTemplate(rule.getTemplateId())) {
                    skipped++;
                    continue;
                }
                var existingQuery = Wrappers.lambdaQuery(InspectionTask.class)
                    .eq(InspectionTask::getTenantCode, tenantCode)
                    .eq(InspectionTask::getWorkOrderId, workOrder.getId())
                    .eq(InspectionTask::getProcessCode, rule.getProcessCode())
                    .eq(InspectionTask::getTaskType, rule.getTaskType());
                if (batch == null) {
                    existingQuery.isNull(InspectionTask::getBatchId);
                } else {
                    existingQuery.eq(InspectionTask::getBatchId, batch.getId());
                }
                InspectionTask existing = inspectionTaskMapper.selectOne(existingQuery);
                if (existing != null) {
                    skipped++;
                    continue;
                }
                InspectionTask task = new InspectionTask();
                task.setId(UUID.randomUUID().toString());
                task.setTenantCode(tenantCode);
                task.setTaskNo(nextTaskNo());
                task.setWorkOrderId(workOrder.getId());
                task.setBatchId(batch == null ? null : batch.getId());
                task.setProcessCode(rule.getProcessCode());
                task.setProcessName(rule.getProcessName());
                task.setTaskType(rule.getTaskType());
                task.setTemplateId(rule.getTemplateId());
                task.setSourceType("AUTO");
                task.setStatus("PENDING");
                task.setCreatedBy(TenantContext.actor());
                task.setCreatedAt(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                inspectionTaskMapper.insert(task);
                generatedTasks.add(InspectionTaskResponse.from(task, workOrder.getWorkOrderNo(), batch == null ? null : batch.getBatchNo()));
            }
        }
        return new GenerationResultResponse(generatedTasks.size(), skipped, generatedTasks);
    }

    private List<ProductionBatch> resolveBatches(WorkOrder workOrder, String batchNo) {
        if (StringUtils.hasText(batchNo)) {
            ProductionBatch batch = productionBatchMapper.selectOne(Wrappers.lambdaQuery(ProductionBatch.class)
                .eq(ProductionBatch::getTenantCode, workOrder.getTenantCode())
                .eq(ProductionBatch::getWorkOrderId, workOrder.getId())
                .eq(ProductionBatch::getBatchNo, batchNo));
            if (batch == null) {
                throw new IllegalArgumentException("该工单下未找到生产批次：" + batchNo);
            }
            return List.of(batch);
        }
        List<ProductionBatch> batches = productionBatchMapper.selectList(Wrappers.lambdaQuery(ProductionBatch.class)
            .eq(ProductionBatch::getTenantCode, workOrder.getTenantCode())
            .eq(ProductionBatch::getWorkOrderId, workOrder.getId())
            .eq(ProductionBatch::getStatus, "ACTIVE"));
        return batches.isEmpty() ? Collections.singletonList(null) : batches;
    }

    private String nextTaskNo() {
        long sequence = inspectionTaskMapper.selectCount(Wrappers.lambdaQuery(InspectionTask.class)
            .eq(InspectionTask::getTenantCode, TenantContext.tenantCode())) + 1;
        return "IT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", sequence);
    }
}
