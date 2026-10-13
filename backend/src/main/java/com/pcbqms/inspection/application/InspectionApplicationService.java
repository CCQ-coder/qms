package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.inspection.api.CreateInspectionRecordRequest;
import com.pcbqms.inspection.api.CreateInspectionTaskRequest;
import com.pcbqms.inspection.api.FinishedTestInspectionRecordResponse;
import com.pcbqms.inspection.api.InspectionRecordResponse;
import com.pcbqms.inspection.api.InspectionTaskResponse;
import com.pcbqms.inspection.api.ProcessInspectionRecordResponse;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.Nonconformity;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.domain.InspectionTemplate;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.NonconformityMapper;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTemplateMapper;
import com.pcbqms.integration.application.InspectionAttachmentApplicationService;
import com.pcbqms.workorder.application.WorkOrderApplicationService;
import com.pcbqms.workorder.domain.ProductionBatch;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class InspectionApplicationService {
    private static final String AUTO_CREATE_NCR = "quality.auto-create-ncr";
    private final InspectionTaskMapper inspectionTaskMapper;
    private final InspectionRecordMapper inspectionRecordMapper;
    private final NonconformityMapper nonconformityMapper;
    private final SystemParameterMapper systemParameterMapper;
    private final InspectionTemplateMapper inspectionTemplateMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ProductionBatchMapper productionBatchMapper;
    private final WorkOrderApplicationService workOrderApplicationService;
    private final InspectionTemplateApplicationService inspectionTemplateApplicationService;
    private final InspectionAttachmentApplicationService inspectionAttachmentApplicationService;
    private final InspectionAccessScopeService inspectionAccessScopeService;
    private final EquipmentMapper equipmentMapper;
    private final ObjectMapper objectMapper;

    public List<InspectionTaskResponse> list(String status) {
        String tenantCode = TenantContext.tenantCode();
        var query = Wrappers.lambdaQuery(InspectionTask.class)
            .eq(InspectionTask::getTenantCode, tenantCode);
        InspectionAccessScopeService.InspectionAccess access = inspectionAccessScopeService.currentAccess();
        if (!access.fullDataAccess()) {
            if (access.processCodes().isEmpty()) {
                return List.of();
            }
            query.in(InspectionTask::getProcessCode, access.processCodes());
        }
        if (StringUtils.hasText(status)) {
            query.eq(InspectionTask::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        return inspectionTaskMapper.selectList(query.orderByDesc(InspectionTask::getCreatedAt)).stream()
            .map(this::toTaskResponse)
            .toList();
    }

    /**
     * 制程已检页使用真实的检验记录而非仅返回已完成任务，以便展示结论、良品数和不良处置状态。
     */
    public List<ProcessInspectionRecordResponse> listCompletedProcessRecords() {
        String tenantCode = TenantContext.tenantCode();
        var taskQuery = Wrappers.lambdaQuery(InspectionTask.class)
            .eq(InspectionTask::getTenantCode, tenantCode)
            .eq(InspectionTask::getTaskType, "PROCESS")
            .eq(InspectionTask::getStatus, "COMPLETED");
        InspectionAccessScopeService.InspectionAccess access = inspectionAccessScopeService.currentAccess();
        if (!access.fullDataAccess()) {
            if (access.processCodes().isEmpty()) {
                return List.of();
            }
            taskQuery.in(InspectionTask::getProcessCode, access.processCodes());
        }
        List<InspectionTask> tasks = inspectionTaskMapper.selectList(taskQuery);
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<String, InspectionTask> tasksById = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(InspectionTask::getId, task -> task));
        return inspectionRecordMapper.selectList(Wrappers.lambdaQuery(InspectionRecord.class)
                .eq(InspectionRecord::getTenantCode, tenantCode)
                .in(InspectionRecord::getTaskId, tasksById.keySet())
                .orderByDesc(InspectionRecord::getInspectedAt))
            .stream()
            .map(record -> toProcessRecordResponse(record, tasksById.get(record.getTaskId())))
            .toList();
    }

    /**
     * Returns submitted FQC/finished-test records rather than task placeholders.
     * The response is intentionally joined with task and work-order context so the
     * workstation page can show the same audit fields as the device feed.
     */
    public List<FinishedTestInspectionRecordResponse> listCompletedFinishedRecords() {
        String tenantCode = TenantContext.tenantCode();
        InspectionAccessScopeService.InspectionAccess access = inspectionAccessScopeService.currentAccess();
        List<InspectionTask> tasks = inspectionTaskMapper.selectList(Wrappers.lambdaQuery(InspectionTask.class)
                .eq(InspectionTask::getTenantCode, tenantCode)
                .eq(InspectionTask::getStatus, "COMPLETED"))
            .stream()
            .filter(task -> isFinishedTestTask(task) && (access.fullDataAccess() || access.canAccess(task.getProcessCode())))
            .toList();
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<String, InspectionTask> tasksById = tasks.stream()
            .collect(java.util.stream.Collectors.toMap(InspectionTask::getId, task -> task));
        return inspectionRecordMapper.selectList(Wrappers.lambdaQuery(InspectionRecord.class)
                .eq(InspectionRecord::getTenantCode, tenantCode)
                .in(InspectionRecord::getTaskId, tasksById.keySet())
                .orderByDesc(InspectionRecord::getInspectedAt))
            .stream()
            .map(record -> toFinishedTestRecordResponse(record, tasksById.get(record.getTaskId())))
            .toList();
    }

    @Transactional
    public InspectionTaskResponse createManualTask(CreateInspectionTaskRequest request) {
        WorkOrder workOrder = workOrderApplicationService.requireWorkOrder(request.workOrderNo());
        ProductionBatch batch = workOrderApplicationService.findBatch(request.batchNo());
        if (batch != null && !batch.getWorkOrderId().equals(workOrder.getId())) {
            throw new IllegalArgumentException("生产批次不属于该工单");
        }
        inspectionTemplateApplicationService.assertActiveTemplateBelongsToTenant(request.templateId());
        LocalDateTime now = LocalDateTime.now();
        InspectionTask task = new InspectionTask();
        task.setId(UUID.randomUUID().toString());
        task.setTenantCode(TenantContext.tenantCode());
        task.setTaskNo(nextTaskNo());
        task.setWorkOrderId(workOrder.getId());
        task.setBatchId(batch == null ? null : batch.getId());
        task.setProcessCode(request.processCode().trim());
        task.setProcessName(request.processName().trim());
        task.setTaskType(request.taskType().trim().toUpperCase(Locale.ROOT));
        task.setTemplateId(request.templateId());
        task.setSourceType("MANUAL");
        task.setStatus("PENDING");
        task.setAssigneeName(request.assigneeName());
        task.setDueAt(request.dueAt());
        task.setCreatedBy(TenantContext.actor());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        inspectionTaskMapper.insert(task);
        return InspectionTaskResponse.from(task, workOrder.getWorkOrderNo(), batch == null ? null : batch.getBatchNo());
    }

    @Transactional
    public InspectionRecordResponse submitRecord(String taskId, CreateInspectionRecordRequest request) {
        String tenantCode = TenantContext.tenantCode();
        InspectionTask task = inspectionTaskMapper.selectById(taskId);
        if (task == null || !tenantCode.equals(task.getTenantCode())) {
            throw new IllegalArgumentException("检验任务不存在或无权操作");
        }
        inspectionAccessScopeService.requireProcessAccess(task.getProcessCode());
        InspectionRecord existingRecord = findExistingRecord(tenantCode, request.clientRequestId());
        if (existingRecord != null) {
            if (!task.getId().equals(existingRecord.getTaskId())) {
                throw new IllegalArgumentException("本地请求标识已用于其他检验任务");
            }
            return InspectionRecordResponse.from(existingRecord, findNcrNo(existingRecord.getId(), tenantCode));
        }
        if ("COMPLETED".equals(task.getStatus())) {
            throw new IllegalArgumentException("该检验任务已完成");
        }
        String verdict = request.verdict().trim().toUpperCase(Locale.ROOT);
        if (!"PASS".equals(verdict) && !"FAIL".equals(verdict)) {
            throw new IllegalArgumentException("检验结论只能是 PASS 或 FAIL");
        }
        inspectionTemplateApplicationService.validateRecordResults(task.getTemplateId(), request.results(), verdict);
        LocalDateTime now = LocalDateTime.now();
        InspectionRecord record = new InspectionRecord();
        record.setId(UUID.randomUUID().toString());
        record.setTenantCode(tenantCode);
        record.setTaskId(task.getId());
        record.setVerdict(verdict);
        record.setDefectiveQuantity(request.defectiveQuantity());
        record.setDefectSummary(request.defectSummary());
        record.setDefectLocation(request.defectLocation());
        record.setResultsJson(toJson(request.results()));
        record.setAttachmentUrlsJson(toJson(request.attachmentUrls()));
        record.setEquipmentAssetNo(resolveEquipmentAssetNo(task, request.equipmentAssetNo()));
        record.setClientRequestId(request.clientRequestId());
        record.setInspectedBy(TenantContext.actor());
        record.setInspectedAt(now);
        record.setCreatedAt(now);
        inspectionRecordMapper.insert(record);
        inspectionAttachmentApplicationService.linkToInspectionRecord(record.getId(), request.attachmentUrls());

        task.setStatus("COMPLETED");
        task.setUpdatedAt(now);
        inspectionTaskMapper.updateById(task);

        String ncrNo = null;
        if ("FAIL".equals(verdict) && isAutoCreateNcrEnabled(tenantCode)) {
            ncrNo = createNonconformity(task, record, now);
        }
        return InspectionRecordResponse.from(record, ncrNo);
    }

    private InspectionTaskResponse toTaskResponse(InspectionTask task) {
        WorkOrder workOrder = workOrderMapper.selectById(task.getWorkOrderId());
        ProductionBatch batch = StringUtils.hasText(task.getBatchId()) ? productionBatchMapper.selectById(task.getBatchId()) : null;
        return InspectionTaskResponse.from(task,
            workOrder == null ? "已删除工单" : workOrder.getWorkOrderNo(),
            batch == null ? null : batch.getBatchNo());
    }

    private boolean isAutoCreateNcrEnabled(String tenantCode) {
        SystemParameter parameter = systemParameterMapper.selectOne(Wrappers.lambdaQuery(SystemParameter.class)
            .eq(SystemParameter::getTenantCode, tenantCode)
            .eq(SystemParameter::getParameterKey, AUTO_CREATE_NCR));
        return parameter != null && Boolean.parseBoolean(parameter.getParameterValue());
    }

    private InspectionRecord findExistingRecord(String tenantCode, String clientRequestId) {
        if (!StringUtils.hasText(clientRequestId)) {
            return null;
        }
        return inspectionRecordMapper.selectOne(Wrappers.lambdaQuery(InspectionRecord.class)
            .eq(InspectionRecord::getTenantCode, tenantCode)
            .eq(InspectionRecord::getClientRequestId, clientRequestId.trim()));
    }

    private String findNcrNo(String inspectionRecordId, String tenantCode) {
        Nonconformity ncr = findNcr(inspectionRecordId, tenantCode);
        return ncr == null ? null : ncr.getNcrNo();
    }

    private Nonconformity findNcr(String inspectionRecordId, String tenantCode) {
        return nonconformityMapper.selectOne(Wrappers.lambdaQuery(Nonconformity.class)
            .eq(Nonconformity::getTenantCode, tenantCode)
            .eq(Nonconformity::getInspectionRecordId, inspectionRecordId));
    }

    private ProcessInspectionRecordResponse toProcessRecordResponse(InspectionRecord record, InspectionTask task) {
        WorkOrder workOrder = task == null ? null : workOrderMapper.selectById(task.getWorkOrderId());
        ProductionBatch batch = task != null && StringUtils.hasText(task.getBatchId())
            ? productionBatchMapper.selectById(task.getBatchId()) : null;
        InspectionTemplate template = task != null && StringUtils.hasText(task.getTemplateId())
            ? inspectionTemplateMapper.selectById(task.getTemplateId()) : null;
        Nonconformity ncr = findNcr(record.getId(), record.getTenantCode());
        int inspectionQuantity = batch != null && batch.getPlannedQuantity() != null ? batch.getPlannedQuantity()
            : workOrder != null && workOrder.getPlannedQuantity() != null ? workOrder.getPlannedQuantity() : 0;
        int defectiveQuantity = record.getDefectiveQuantity() == null ? 0 : record.getDefectiveQuantity();
        int goodQuantity = Math.max(inspectionQuantity - defectiveQuantity, 0);
        String followUpStatus = "PASS".equals(record.getVerdict()) ? "无需处置"
            : ncr == null ? "待处置不良" : "CLOSED".equals(ncr.getStatus()) ? "处置已关闭" : "不良处理中";
        return new ProcessInspectionRecordResponse(
            record.getId(), task == null ? "已删除任务" : task.getTaskNo(),
            task == null ? "—" : task.getProcessCode(), task == null ? "未匹配工序" : task.getProcessName(),
            workOrder == null ? "已删除工单" : workOrder.getWorkOrderNo(), batch == null ? null : batch.getBatchNo(),
            workOrder == null ? null : workOrder.getInternalProductNo(),
            workOrder == null ? null : firstNonBlank(workOrder.getCustomerPartNo(), workOrder.getProductCode()),
            inspectionQuantity, goodQuantity, defectiveQuantity,
            template == null ? "通用检验记录" : template.getName(), template == null ? null : template.getVersionNo(),
            record.getInspectedBy(), record.getVerdict(), followUpStatus, ncr == null ? null : ncr.getNcrNo(),
            record.getDefectSummary(), record.getDefectLocation(), readResults(record.getResultsJson()),
            readAttachmentUrls(record.getAttachmentUrlsJson()), record.getInspectedAt()
        );
    }

    private FinishedTestInspectionRecordResponse toFinishedTestRecordResponse(InspectionRecord record, InspectionTask task) {
        WorkOrder workOrder = task == null ? null : workOrderMapper.selectById(task.getWorkOrderId());
        ProductionBatch batch = task != null && StringUtils.hasText(task.getBatchId())
            ? productionBatchMapper.selectById(task.getBatchId()) : null;
        int inspectionQuantity = batch != null && batch.getPlannedQuantity() != null ? batch.getPlannedQuantity()
            : workOrder != null && workOrder.getPlannedQuantity() != null ? workOrder.getPlannedQuantity() : 0;
        int defectiveQuantity = Math.max(record.getDefectiveQuantity() == null ? 0 : record.getDefectiveQuantity(), 0);
        return new FinishedTestInspectionRecordResponse(
            record.getId(), task == null ? "已删除任务" : task.getTaskNo(), record.getInspectedAt(),
            record.getEquipmentAssetNo(), workOrder == null ? "已删除工单" : workOrder.getWorkOrderNo(),
            batch == null ? null : batch.getBatchNo(), workOrder == null ? null : workOrder.getInternalProductNo(),
            workOrder == null ? null : firstNonBlank(workOrder.getCustomerPartNo(), workOrder.getProductCode()),
            inspectionQuantity, Math.max(inspectionQuantity - defectiveQuantity, 0), defectiveQuantity,
            record.getVerdict(), record.getDefectSummary(), record.getDefectLocation(), record.getInspectedBy(),
            readResults(record.getResultsJson())
        );
    }

    private boolean isFinishedTestTask(InspectionTask task) {
        return "FINISHED".equalsIgnoreCase(task.getTaskType()) || "FQC".equalsIgnoreCase(task.getProcessCode());
    }

    private String resolveEquipmentAssetNo(InspectionTask task, String equipmentAssetNo) {
        if (!StringUtils.hasText(equipmentAssetNo)) {
            return null;
        }
        Equipment equipment = equipmentMapper.selectOne(Wrappers.lambdaQuery(Equipment.class)
            .eq(Equipment::getTenantCode, TenantContext.tenantCode())
            .eq(Equipment::getAssetNo, equipmentAssetNo.trim()));
        if (equipment == null) {
            throw new IllegalArgumentException("检测设备不存在或无权使用");
        }
        if (isFinishedTestTask(task) && !"成品测试设备".equals(equipment.getEquipmentType())) {
            throw new IllegalArgumentException("FQC 检验记录只能关联成品测试设备");
        }
        return equipment.getAssetNo();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Map<String, Object> readResults(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private List<String> readAttachmentUrls(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String createNonconformity(InspectionTask task, InspectionRecord record, LocalDateTime now) {
        long sequence = nonconformityMapper.selectCount(Wrappers.lambdaQuery(Nonconformity.class)
            .eq(Nonconformity::getTenantCode, task.getTenantCode())) + 1;
        String ncrNo = "NCR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", sequence);
        Nonconformity ncr = new Nonconformity();
        ncr.setId(UUID.randomUUID().toString());
        ncr.setTenantCode(task.getTenantCode());
        ncr.setNcrNo(ncrNo);
        ncr.setInspectionRecordId(record.getId());
        ncr.setWorkOrderId(task.getWorkOrderId());
        ncr.setBatchId(task.getBatchId());
        ncr.setStatus("OPEN");
        ncr.setInitiatedBy(TenantContext.actor());
        ncr.setCreatedAt(now);
        nonconformityMapper.insert(ncr);
        return ncrNo;
    }

    private String nextTaskNo() {
        long sequence = inspectionTaskMapper.selectCount(Wrappers.lambdaQuery(InspectionTask.class)
            .eq(InspectionTask::getTenantCode, TenantContext.tenantCode())) + 1;
        return "IT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", sequence);
    }

    private String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("检验结果无法序列化", exception);
        }
    }
}
