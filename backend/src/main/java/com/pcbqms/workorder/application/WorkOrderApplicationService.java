package com.pcbqms.workorder.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.workorder.api.CreateWorkOrderRequest;
import com.pcbqms.workorder.api.ScanLookupResponse;
import com.pcbqms.workorder.api.WorkOrderPageResponse;
import com.pcbqms.workorder.api.WorkOrderQueryRequest;
import com.pcbqms.workorder.api.WorkOrderResponse;
import com.pcbqms.workorder.domain.ProductionBatch;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WorkOrderApplicationService {
    private final WorkOrderMapper workOrderMapper;
    private final ProductionBatchMapper productionBatchMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public List<WorkOrderResponse> list(String keyword) {
        return findMatchingWorkOrders(keyword, null, null, null, null, null, null, null, null, null, null, null)
            .stream().map(this::toResponse).toList();
    }

    public WorkOrderPageResponse page(WorkOrderQueryRequest request) {
        var query = buildWorkOrderQuery(
            request.keyword(), request.batchNo(), request.customerCode(), request.currentProcess(), request.status(),
            request.qualityStatus(), request.warehouseStatus(), request.priority(), request.inputDateFrom(), request.inputDateTo(),
            request.requiredDeliveryDateFrom(), request.requiredDeliveryDateTo());
        long total = workOrderMapper.selectCount(query);
        long page = request.resolvedPage();
        long pageSize = request.resolvedPageSize();
        long offset = (page - 1) * pageSize;
        List<WorkOrder> rows = offset >= total ? List.of() : workOrderMapper.selectList(query
            .orderByDesc(WorkOrder::getUpdatedAt)
            .last("LIMIT " + pageSize + " OFFSET " + offset));
        return new WorkOrderPageResponse(rows.stream().map(this::toResponse).toList(), total, page, pageSize);
    }

    private List<WorkOrder> findMatchingWorkOrders(
        String keyword, String batchNo, String customerCode, String currentProcess, String status, String qualityStatus,
        String warehouseStatus, String priority, LocalDate inputDateFrom, LocalDate inputDateTo,
        LocalDate requiredDeliveryDateFrom, LocalDate requiredDeliveryDateTo
    ) {
        return workOrderMapper.selectList(buildWorkOrderQuery(keyword, batchNo, customerCode, currentProcess, status, qualityStatus,
            warehouseStatus, priority, inputDateFrom, inputDateTo, requiredDeliveryDateFrom, requiredDeliveryDateTo)
            .orderByDesc(WorkOrder::getUpdatedAt));
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkOrder> buildWorkOrderQuery(
        String keyword, String batchNo, String customerCode, String currentProcess, String status, String qualityStatus,
        String warehouseStatus, String priority, LocalDate inputDateFrom, LocalDate inputDateTo,
        LocalDate requiredDeliveryDateFrom, LocalDate requiredDeliveryDateTo
    ) {
        String tenantCode = TenantContext.tenantCode();
        InspectionAccessScopeService.InspectionAccess access = inspectionAccessScopeService.currentAccess();
        var query = Wrappers.lambdaQuery(WorkOrder.class)
            .eq(WorkOrder::getTenantCode, tenantCode);
        if (!access.fullDataAccess()) {
            List<String> visibleWorkOrderIds = inspectionTaskMapper.selectList(Wrappers.lambdaQuery(InspectionTask.class)
                    .eq(InspectionTask::getTenantCode, tenantCode))
                .stream()
                .filter(task -> access.canAccess(task.getProcessCode()))
                .map(InspectionTask::getWorkOrderId)
                .distinct()
                .toList();
            if (visibleWorkOrderIds.isEmpty()) {
                return query.apply("1 = 0");
            }
            query.in(WorkOrder::getId, visibleWorkOrderIds);
        }
        if (StringUtils.hasText(batchNo)) {
            List<String> workOrderIds = productionBatchMapper.selectList(Wrappers.lambdaQuery(ProductionBatch.class)
                    .eq(ProductionBatch::getTenantCode, tenantCode)
                    .like(ProductionBatch::getBatchNo, batchNo.trim()))
                .stream().map(ProductionBatch::getWorkOrderId).distinct().toList();
            if (workOrderIds.isEmpty()) {
                return query.apply("1 = 0");
            }
            query.in(WorkOrder::getId, workOrderIds);
        }
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            query.and(wrapper -> wrapper
                .like(WorkOrder::getWorkOrderNo, text)
                .or()
                .like(WorkOrder::getProductCode, text)
                .or()
                .like(WorkOrder::getProductName, text)
                .or()
                .like(WorkOrder::getInternalProductNo, text)
                .or()
                .like(WorkOrder::getCustomerPartNo, text)
                .or()
                .like(WorkOrder::getCustomerOrderNo, text)
                .or()
                .like(WorkOrder::getProductionNoticeNo, text));
        }
        if (StringUtils.hasText(customerCode)) query.eq(WorkOrder::getCustomerCode, customerCode.trim());
        if (StringUtils.hasText(currentProcess)) {
            String text = currentProcess.trim();
            query.and(wrapper -> wrapper.like(WorkOrder::getCurrentProcessCode, text)
                .or().like(WorkOrder::getCurrentProcessName, text));
        }
        if (StringUtils.hasText(status)) query.eq(WorkOrder::getStatus, status.trim());
        if (StringUtils.hasText(qualityStatus)) query.eq(WorkOrder::getQualityStatus, qualityStatus.trim());
        if (StringUtils.hasText(warehouseStatus)) query.eq(WorkOrder::getWarehouseStatus, warehouseStatus.trim());
        if (StringUtils.hasText(priority)) query.eq(WorkOrder::getPriority, priority.trim());
        if (inputDateFrom != null) query.ge(WorkOrder::getInputDate, inputDateFrom);
        if (inputDateTo != null) query.le(WorkOrder::getInputDate, inputDateTo);
        if (requiredDeliveryDateFrom != null) query.ge(WorkOrder::getRequiredDeliveryDate, requiredDeliveryDateFrom);
        if (requiredDeliveryDateTo != null) query.le(WorkOrder::getRequiredDeliveryDate, requiredDeliveryDateTo);
        return query;
    }

    @Transactional
    public WorkOrderResponse upsert(CreateWorkOrderRequest request) {
        inspectionAccessScopeService.requireSupervisorOrAdministrator();
        return upsertInternal(request);
    }

    /** Trusted local gateways are authenticated separately and do not impersonate a QMS operator. */
    @Transactional
    public WorkOrderResponse upsertFromIntegration(CreateWorkOrderRequest request) {
        return upsertInternal(request);
    }

    /**
     * Reconciles a complete ERP snapshot without erasing QMS audit evidence.
     * The prefix prevents one gateway feed from affecting records owned by a
     * different gateway or created manually in QMS.
     */
    @Transactional
    public int deactivateMissingGatewayWorkOrders(String sourceKeyPrefix, List<String> currentSourceKeys) {
        if (!"ERP-EXCEL:".equals(sourceKeyPrefix)) {
            throw new IllegalArgumentException("不支持的 ERP 来源键前缀");
        }
        String tenantCode = TenantContext.tenantCode();
        Set<String> currentKeys = new HashSet<>(currentSourceKeys);
        List<WorkOrder> importedWorkOrders = workOrderMapper.selectList(Wrappers.lambdaQuery(WorkOrder.class)
            .eq(WorkOrder::getTenantCode, tenantCode)
            .eq(WorkOrder::getSourceSystem, "GATEWAY")
            .eq(WorkOrder::getStatus, "ACTIVE")
            .likeRight(WorkOrder::getSourceKey, sourceKeyPrefix));
        LocalDateTime now = LocalDateTime.now();
        int deactivated = 0;
        for (WorkOrder workOrder : importedWorkOrders) {
            if (!currentKeys.contains(workOrder.getSourceKey())) {
                workOrder.setStatus("INACTIVE");
                workOrder.setUpdatedAt(now);
                workOrderMapper.updateById(workOrder);
                deactivated++;
            }
        }
        return deactivated;
    }

    /** OCR confirmation already checks the uploader and target process scope before reaching this method. */
    @Transactional
    public WorkOrderResponse upsertFromOcrConfirmation(CreateWorkOrderRequest request) {
        return upsertInternal(request);
    }

    private WorkOrderResponse upsertInternal(CreateWorkOrderRequest request) {
        String tenantCode = TenantContext.tenantCode();
        LocalDateTime now = LocalDateTime.now();
        WorkOrder workOrder = findByWorkOrderNo(tenantCode, request.workOrderNo());
        boolean isNew = workOrder == null;
        if (isNew) {
            workOrder = new WorkOrder();
            workOrder.setId(UUID.randomUUID().toString());
            workOrder.setTenantCode(tenantCode);
            workOrder.setWorkOrderNo(request.workOrderNo().trim());
            workOrder.setCreatedAt(now);
            workOrder.setCompletedQuantity(0);
            workOrder.setStatus("ACTIVE");
            workOrder.setWarehouseStatus("PENDING");
            workOrder.setPriority("NORMAL");
            workOrder.setQualityStatus("UNASSESSED");
        }
        workOrder.setProductCode(request.productCode().trim());
        workOrder.setProductName(request.productName());
        workOrder.setSpecification(request.specification());
        workOrder.setCustomerCode(request.customerCode());
        workOrder.setInternalProductNo(request.internalProductNo());
        workOrder.setCustomerPartNo(request.customerPartNo());
        workOrder.setProcessTechnology(request.processTechnology());
        workOrder.setMaterial(request.material());
        workOrder.setBoardThickness(request.boardThickness());
        workOrder.setCopperThickness(request.copperThickness());
        if (StringUtils.hasText(request.warehouseStatus())) {
            workOrder.setWarehouseStatus(request.warehouseStatus());
        }
        workOrder.setMaterialInputDate(request.materialInputDate());
        workOrder.setPcsLength(request.pcsLength());
        workOrder.setPcsWidth(request.pcsWidth());
        workOrder.setMaterialNo(request.materialNo());
        workOrder.setMaterialName(request.materialName());
        workOrder.setMaterialSpecification(request.materialSpecification());
        workOrder.setStandardMaterialConsumption(request.standardMaterialConsumption());
        if (request.materialInputQuantity() != null) workOrder.setMaterialInputQuantity(request.materialInputQuantity());
        workOrder.setCumulativeWarehouseQuantity(request.cumulativeWarehouseQuantity());
        if (request.cumulativeScrapQuantity() != null) workOrder.setCumulativeScrapQuantity(request.cumulativeScrapQuantity());
        if (request.cumulativeDefectQuantity() != null) workOrder.setCumulativeDefectQuantity(request.cumulativeDefectQuantity());
        workOrder.setRequiredDeliveryDate(request.requiredDeliveryDate());
        workOrder.setProductionNoticeNo(request.productionNoticeNo());
        workOrder.setInputDate(request.inputDate());
        workOrder.setSinglePcsArea(request.singlePcsArea());
        if (request.pnlLength() != null) workOrder.setPnlLength(request.pnlLength());
        if (request.pnlWidth() != null) workOrder.setPnlWidth(request.pnlWidth());
        workOrder.setABoardLength(request.aBoardLength());
        workOrder.setABoardWidth(request.aBoardWidth());
        workOrder.setBBoardLength(request.bBoardLength());
        workOrder.setBBoardWidth(request.bBoardWidth());
        workOrder.setFormingMethod(request.formingMethod());
        workOrder.setLayerCount(request.layerCount());
        workOrder.setProductionNoticeQuantity(request.productionNoticeQuantity());
        if (request.productionNoticeOrderQuantity() != null) workOrder.setProductionNoticeOrderQuantity(request.productionNoticeOrderQuantity());
        workOrder.setCustomerOrder(request.customerOrder());
        workOrder.setCustomerOrderNo(request.customerOrderNo());
        workOrder.setCustomerOrderQuantity(request.customerOrderQuantity());
        workOrder.setMaterialArea(request.materialArea());
        workOrder.setProductionNoticeArea(request.productionNoticeArea());
        workOrder.setCustomerOrderArea(request.customerOrderArea());
        if (request.unreceivedQuantity() != null) workOrder.setUnreceivedQuantity(request.unreceivedQuantity());
        if (request.lastWarehouseDate() != null) workOrder.setLastWarehouseDate(request.lastWarehouseDate());
        if (request.totalPanelQuantity() != null) workOrder.setTotalPanelQuantity(request.totalPanelQuantity());
        if (request.aBoardPanelQuantity() != null) workOrder.setABoardPanelQuantity(request.aBoardPanelQuantity());
        if (request.bBoardPanelQuantity() != null) workOrder.setBBoardPanelQuantity(request.bBoardPanelQuantity());
        if (StringUtils.hasText(request.panelizationNo())) workOrder.setPanelizationNo(request.panelizationNo().trim());
        if (request.estimatedYieldRate() != null) workOrder.setEstimatedYieldRate(request.estimatedYieldRate());
        if (request.plannedMaterialIssueQuantity() != null) workOrder.setPlannedMaterialIssueQuantity(request.plannedMaterialIssueQuantity());
        if (StringUtils.hasText(request.priority())) {
            workOrder.setPriority(request.priority().trim());
        }
        if (StringUtils.hasText(request.processRouteVersion())) {
            workOrder.setProcessRouteVersion(request.processRouteVersion().trim());
        }
        if (StringUtils.hasText(request.miVersion())) {
            workOrder.setMiVersion(request.miVersion().trim());
        }
        workOrder.setReleasedAt(request.releasedAt() != null
            ? request.releasedAt()
            : (request.materialInputDate() != null ? request.materialInputDate() : (isNew ? LocalDate.now() : workOrder.getReleasedAt())));
        workOrder.setCurrentProcessCode(request.currentProcessCode().trim());
        workOrder.setCurrentProcessName(request.currentProcessName().trim());
        workOrder.setPlannedQuantity(request.plannedQuantity());
        workOrder.setSourceSystem(StringUtils.hasText(request.sourceSystem()) ? request.sourceSystem() : "MANUAL");
        workOrder.setSourceKey(request.sourceKey());
        workOrder.setSourceVersion(request.sourceVersion());
        // A source record that was previously soft-deleted may legitimately
        // reappear in a later complete ERP export. Restore only this Excel
        // gateway's records; manual or other gateway statuses stay untouched.
        if ("GATEWAY".equals(request.sourceSystem())
            && request.sourceKey() != null
            && request.sourceKey().startsWith("ERP-EXCEL:")
            && "INACTIVE".equals(workOrder.getStatus())) {
            workOrder.setStatus("ACTIVE");
        }
        workOrder.setUpdatedAt(now);

        if (isNew) {
            workOrderMapper.insert(workOrder);
        } else {
            workOrderMapper.updateById(workOrder);
        }

        if (StringUtils.hasText(request.batchNo())) {
            upsertBatch(workOrder, request.batchNo().trim(), request.plannedQuantity(), now);
        }
        return toResponse(workOrder);
    }

    public ScanLookupResponse lookupByScanCode(String code) {
        String tenantCode = TenantContext.tenantCode();
        WorkOrder workOrder = findByWorkOrderNo(tenantCode, code);
        ProductionBatch scannedBatch = null;
        if (workOrder == null) {
            scannedBatch = productionBatchMapper.selectOne(Wrappers.lambdaQuery(ProductionBatch.class)
                .eq(ProductionBatch::getTenantCode, tenantCode)
                .eq(ProductionBatch::getBatchNo, code));
            if (scannedBatch != null) {
                workOrder = workOrderMapper.selectById(scannedBatch.getWorkOrderId());
            }
        }
        if (workOrder == null || !tenantCode.equals(workOrder.getTenantCode())) {
            throw new IllegalArgumentException("未找到工单或生产批次：" + code);
        }
        InspectionAccessScopeService.InspectionAccess access = inspectionAccessScopeService.currentAccess();
        var taskQuery = Wrappers.lambdaQuery(InspectionTask.class)
            .eq(InspectionTask::getTenantCode, tenantCode)
            .eq(InspectionTask::getWorkOrderId, workOrder.getId())
            .orderByDesc(InspectionTask::getCreatedAt);
        if (scannedBatch != null) {
            taskQuery.eq(InspectionTask::getBatchId, scannedBatch.getId());
        }
        List<InspectionTask> accessibleTasks = inspectionTaskMapper.selectList(taskQuery).stream()
            .filter(task -> access.canAccess(task.getProcessCode()))
            .toList();
        if (!access.fullDataAccess() && accessibleTasks.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException("无权查看该工单或批次的检验数据");
        }
        List<String> taskNos = accessibleTasks.stream()
            .filter(task -> !"COMPLETED".equals(task.getStatus()))
            .map(InspectionTask::getTaskNo)
            .toList();
        return new ScanLookupResponse(toResponse(workOrder), taskNos);
    }

    public WorkOrder requireWorkOrder(String workOrderNo) {
        WorkOrder workOrder = findByWorkOrderNo(TenantContext.tenantCode(), workOrderNo);
        if (workOrder == null) {
            throw new IllegalArgumentException("工单不存在：" + workOrderNo);
        }
        return workOrder;
    }

    public ProductionBatch findBatch(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            return null;
        }
        return productionBatchMapper.selectOne(Wrappers.lambdaQuery(ProductionBatch.class)
            .eq(ProductionBatch::getTenantCode, TenantContext.tenantCode())
            .eq(ProductionBatch::getBatchNo, batchNo));
    }

    private WorkOrder findByWorkOrderNo(String tenantCode, String workOrderNo) {
        return workOrderMapper.selectOne(Wrappers.lambdaQuery(WorkOrder.class)
            .eq(WorkOrder::getTenantCode, tenantCode)
            .eq(WorkOrder::getWorkOrderNo, workOrderNo.trim()));
    }

    private void upsertBatch(WorkOrder workOrder, String batchNo, int plannedQuantity, LocalDateTime now) {
        ProductionBatch batch = productionBatchMapper.selectOne(Wrappers.lambdaQuery(ProductionBatch.class)
            .eq(ProductionBatch::getTenantCode, workOrder.getTenantCode())
            .eq(ProductionBatch::getBatchNo, batchNo));
        boolean isNew = batch == null;
        if (batch == null) {
            batch = new ProductionBatch();
            batch.setId(UUID.randomUUID().toString());
            batch.setTenantCode(workOrder.getTenantCode());
            batch.setBatchNo(batchNo);
            batch.setStatus("ACTIVE");
            batch.setCreatedAt(now);
        }
        batch.setWorkOrderId(workOrder.getId());
        batch.setPlannedQuantity(plannedQuantity);
        batch.setUpdatedAt(now);
        if (isNew) {
            productionBatchMapper.insert(batch);
        } else {
            productionBatchMapper.updateById(batch);
        }
    }

    private WorkOrderResponse toResponse(WorkOrder workOrder) {
        List<ProductionBatch> batches = productionBatchMapper.selectList(Wrappers.lambdaQuery(ProductionBatch.class)
            .eq(ProductionBatch::getTenantCode, workOrder.getTenantCode())
            .eq(ProductionBatch::getWorkOrderId, workOrder.getId())
            .orderByAsc(ProductionBatch::getBatchNo));
        return WorkOrderResponse.from(workOrder, batches);
    }
}
