package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.api.CapaResponse;
import com.pcbqms.inspection.api.CloseCapaRequest;
import com.pcbqms.inspection.api.CreateCapaRequest;
import com.pcbqms.inspection.api.CreateNonconformityRequest;
import com.pcbqms.inspection.api.CreateManualNonconformityRequest;
import com.pcbqms.inspection.api.NonconformityDetailResponse;
import com.pcbqms.inspection.api.NonconformityResponse;
import com.pcbqms.inspection.domain.Capa;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.Nonconformity;
import com.pcbqms.inspection.domain.NonconformityDetail;
import com.pcbqms.inspection.infrastructure.persistence.CapaMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.NonconformityMapper;
import com.pcbqms.inspection.infrastructure.persistence.NonconformityDetailMapper;
import com.pcbqms.workorder.domain.ProductionBatch;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class NonconformityApplicationService {
    private final NonconformityMapper nonconformityMapper;
    private final CapaMapper capaMapper;
    private final InspectionRecordMapper inspectionRecordMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;
    private final WorkOrderMapper workOrderMapper;
    private final ProductionBatchMapper productionBatchMapper;
    private final NonconformityDetailMapper nonconformityDetailMapper;

    public List<NonconformityResponse> listNonconformities(String status) {
        String tenantCode = TenantContext.tenantCode();
        var access = inspectionAccessScopeService.currentAccess();
        var query = Wrappers.lambdaQuery(Nonconformity.class)
            .eq(Nonconformity::getTenantCode, tenantCode);
        if (StringUtils.hasText(status)) {
            query.eq(Nonconformity::getStatus, status.trim().toUpperCase());
        }
        return nonconformityMapper.selectList(query.orderByDesc(Nonconformity::getCreatedAt)).stream()
            .filter(ncr -> canAccessNonconformity(ncr, access))
            .map(this::toNonconformityResponse)
            .toList();
    }

    public List<CapaResponse> listCapas(String status) {
        String tenantCode = TenantContext.tenantCode();
        var access = inspectionAccessScopeService.currentAccess();
        var query = Wrappers.lambdaQuery(Capa.class).eq(Capa::getTenantCode, tenantCode);
        if (StringUtils.hasText(status)) {
            query.eq(Capa::getStatus, status.trim().toUpperCase());
        }
        return capaMapper.selectList(query.orderByDesc(Capa::getCreatedAt)).stream()
            .filter(capa -> canAccessCapa(capa, access))
            .map(this::toCapaResponse)
            .toList();
    }

    @Transactional
    public NonconformityResponse createNonconformity(CreateNonconformityRequest request) {
        String tenantCode = TenantContext.tenantCode();
        InspectionRecord record = inspectionRecordMapper.selectById(request.inspectionRecordId());
        if (record == null || !tenantCode.equals(record.getTenantCode())) {
            throw new IllegalArgumentException("检验记录不存在或无权操作");
        }
        if (!"FAIL".equals(record.getVerdict())) {
            throw new IllegalArgumentException("仅不合格检验记录可以发起不良品处置");
        }
        InspectionTask task = inspectionTaskMapper.selectById(record.getTaskId());
        if (task == null || !tenantCode.equals(task.getTenantCode())) {
            throw new IllegalArgumentException("检验记录关联任务不存在");
        }
        inspectionAccessScopeService.requireProcessAccess(task.getProcessCode());
        Nonconformity existing = nonconformityMapper.selectOne(Wrappers.lambdaQuery(Nonconformity.class)
            .eq(Nonconformity::getTenantCode, tenantCode)
            .eq(Nonconformity::getInspectionRecordId, record.getId()));
        if (existing != null) {
            throw new IllegalArgumentException("该不合格记录已创建不良品处置单：" + existing.getNcrNo());
        }
        LocalDateTime now = LocalDateTime.now();
        Nonconformity ncr = new Nonconformity();
        ncr.setId(UUID.randomUUID().toString());
        ncr.setTenantCode(tenantCode);
        ncr.setNcrNo(nextNcrNo());
        ncr.setInspectionRecordId(record.getId());
        ncr.setWorkOrderId(task.getWorkOrderId());
        ncr.setBatchId(task.getBatchId());
        ncr.setStatus("OPEN");
        ncr.setDetectionDate(record.getInspectedAt() == null ? LocalDate.now() : record.getInspectedAt().toLocalDate());
        ncr.setProcessCode(task.getProcessCode());
        ncr.setProcessName(task.getProcessName());
        ncr.setRemark(record.getDefectSummary());
        ncr.setDispositionMethod("SCRAP");
        ProductionBatch batch = StringUtils.hasText(task.getBatchId()) ? productionBatchMapper.selectById(task.getBatchId()) : null;
        ncr.setBatchNo(batch == null ? null : batch.getBatchNo());
        ncr.setInitiatedBy(TenantContext.actor());
        ncr.setCreatedAt(now);
        nonconformityMapper.insert(ncr);
        WorkOrder workOrder = workOrderMapper.selectById(task.getWorkOrderId());
        if (workOrder != null && record.getDefectiveQuantity() != null && record.getDefectiveQuantity() > 0) {
            NonconformityDetail detail = newDetail(ncr, 1, workOrder, "PCS", record.getDefectiveQuantity(),
                record.getDefectSummary(), record.getDefectLocation());
            nonconformityDetailMapper.insert(detail);
        }
        return NonconformityResponse.from(ncr, workOrder == null ? "已删除工单" : workOrder.getWorkOrderNo(),
            batch == null ? null : batch.getBatchNo(), detailsFor(ncr));
    }

    @Transactional
    public NonconformityResponse createManualNonconformity(CreateManualNonconformityRequest request) {
        inspectionAccessScopeService.requireProcessAccess(request.processCode().trim());
        String tenantCode = TenantContext.tenantCode();
        List<WorkOrder> workOrders = new ArrayList<>();
        for (var line : request.details()) {
            WorkOrder workOrder = workOrderMapper.selectOne(Wrappers.lambdaQuery(WorkOrder.class)
                .eq(WorkOrder::getTenantCode, tenantCode)
                .eq(WorkOrder::getWorkOrderNo, line.workOrderNo().trim()));
            if (workOrder == null || !tenantCode.equals(workOrder.getTenantCode())) {
                throw new IllegalArgumentException("生产指令不存在或无权操作：" + line.workOrderNo());
            }
            workOrders.add(workOrder);
        }
        Nonconformity ncr = new Nonconformity();
        ncr.setId(UUID.randomUUID().toString());
        ncr.setTenantCode(tenantCode);
        ncr.setNcrNo(nextNcrNo());
        ncr.setWorkOrderId(workOrders.getFirst().getId());
        ncr.setDetectionDate(request.detectionDate());
        ncr.setShiftName(request.shiftName());
        ncr.setProcessCode(request.processCode().trim());
        ncr.setProcessName(request.processName().trim());
        ncr.setResponsibleDepartment(request.responsibleDepartment());
        ncr.setRemark(request.remark());
        ncr.setDispositionMethod(StringUtils.hasText(request.dispositionMethod()) ? request.dispositionMethod().trim() : "SCRAP");
        ncr.setBatchNo(request.batchNo());
        ncr.setStatus("OPEN");
        ncr.setInitiatedBy(TenantContext.actor());
        ncr.setCreatedAt(LocalDateTime.now());
        nonconformityMapper.insert(ncr);
        List<NonconformityDetailResponse> responses = new ArrayList<>();
        for (int index = 0; index < request.details().size(); index++) {
            var line = request.details().get(index);
            WorkOrder workOrder = workOrders.get(index);
            NonconformityDetail detail = newDetail(ncr, index + 1, workOrder, line.unit(), line.scrapQuantity(),
                line.scrapReason(), line.remark());
            nonconformityDetailMapper.insert(detail);
            responses.add(NonconformityDetailResponse.from(detail, workOrder.getWorkOrderNo(), workOrder.getInternalProductNo()));
        }
        return NonconformityResponse.from(ncr, workOrders.getFirst().getWorkOrderNo(), request.batchNo(), responses);
    }

    private NonconformityDetail newDetail(Nonconformity ncr, int lineNo, WorkOrder workOrder, String unit,
                                          int scrapQuantity, String scrapReason, String remark) {
        NonconformityDetail detail = new NonconformityDetail();
        detail.setId(UUID.randomUUID().toString());
        detail.setTenantCode(ncr.getTenantCode());
        detail.setNonconformityId(ncr.getId());
        detail.setLineNo(lineNo);
        detail.setWorkOrderId(workOrder.getId());
        detail.setProcessCode(ncr.getProcessCode());
        detail.setProcessName(ncr.getProcessName());
        detail.setUnit(StringUtils.hasText(unit) ? unit.trim() : "PCS");
        detail.setSinglePcsArea(workOrder.getSinglePcsArea());
        detail.setScrapQuantity(scrapQuantity);
        detail.setScrapArea(workOrder.getSinglePcsArea() == null ? null
            : workOrder.getSinglePcsArea().multiply(BigDecimal.valueOf(scrapQuantity)));
        detail.setScrapReason(scrapReason);
        detail.setRemark(remark);
        detail.setStatus("OPEN");
        return detail;
    }

    @Transactional
    public CapaResponse createCapa(String nonconformityId, CreateCapaRequest request) {
        Nonconformity ncr = requireNcr(nonconformityId);
        requireNonconformityProcessAccess(ncr);
        Capa existing = capaMapper.selectOne(Wrappers.lambdaQuery(Capa.class)
            .eq(Capa::getTenantCode, TenantContext.tenantCode())
            .eq(Capa::getNonconformityId, nonconformityId));
        if (existing != null) {
            throw new IllegalArgumentException("该不良品处置单已关联 CAPA：" + existing.getCapaNo());
        }
        Capa capa = new Capa();
        capa.setId(UUID.randomUUID().toString());
        capa.setTenantCode(TenantContext.tenantCode());
        capa.setCapaNo(nextCapaNo());
        capa.setNonconformityId(ncr.getId());
        capa.setProblemDescription(request.problemDescription().trim());
        capa.setTeamMembers(request.teamMembers());
        capa.setContainmentAction(request.containmentAction());
        capa.setRootCause(request.rootCause());
        capa.setCorrectiveAction(request.correctiveAction());
        capa.setPreventiveAction(request.preventiveAction());
        capa.setStatus("OPEN");
        capa.setInitiatedBy(TenantContext.actor());
        capa.setCreatedAt(LocalDateTime.now());
        capaMapper.insert(capa);
        ncr.setStatus("CAPA_OPEN");
        nonconformityMapper.updateById(ncr);
        updateDetailStatus(ncr, "CAPA_OPEN");
        return toCapaResponse(capa);
    }

    @Transactional
    public CapaResponse closeCapa(String capaId, CloseCapaRequest request) {
        inspectionAccessScopeService.requireSupervisorOrAdministrator();
        Capa capa = capaMapper.selectById(capaId);
        if (capa == null || !TenantContext.tenantCode().equals(capa.getTenantCode())) {
            throw new IllegalArgumentException("CAPA 不存在或无权操作");
        }
        if ("CLOSED".equals(capa.getStatus())) {
            throw new IllegalArgumentException("CAPA 已关闭");
        }
        LocalDateTime now = LocalDateTime.now();
        capa.setVerificationResult(request.verificationResult().trim());
        capa.setRecognitionNotes(request.recognitionNotes());
        capa.setStatus("CLOSED");
        capa.setApprovedBy(TenantContext.actor());
        capa.setClosedAt(now);
        capaMapper.updateById(capa);
        Nonconformity ncr = requireNcr(capa.getNonconformityId());
        ncr.setStatus("CLOSED");
        nonconformityMapper.updateById(ncr);
        updateDetailStatus(ncr, "CLOSED");
        return toCapaResponse(capa);
    }

    private Nonconformity requireNcr(String id) {
        Nonconformity ncr = nonconformityMapper.selectById(id);
        if (ncr == null || !TenantContext.tenantCode().equals(ncr.getTenantCode())) {
            throw new IllegalArgumentException("不良品处置单不存在或无权操作");
        }
        return ncr;
    }

    private void requireNonconformityProcessAccess(Nonconformity ncr) {
        if (!StringUtils.hasText(ncr.getInspectionRecordId())) {
            inspectionAccessScopeService.requireProcessAccess(ncr.getProcessCode());
            return;
        }
        InspectionRecord record = inspectionRecordMapper.selectById(ncr.getInspectionRecordId());
        if (record == null || !TenantContext.tenantCode().equals(record.getTenantCode())) {
            throw new IllegalArgumentException("不良品处置单关联的检验记录不存在");
        }
        InspectionTask task = inspectionTaskMapper.selectById(record.getTaskId());
        if (task == null || !TenantContext.tenantCode().equals(task.getTenantCode())) {
            throw new IllegalArgumentException("不良品处置单关联的检验任务不存在");
        }
        inspectionAccessScopeService.requireProcessAccess(task.getProcessCode());
    }

    private void updateDetailStatus(Nonconformity ncr, String status) {
        nonconformityDetailMapper.update(null, Wrappers.<NonconformityDetail>update()
            .eq("tenant_code", ncr.getTenantCode())
            .eq("nonconformity_id", ncr.getId())
            .set("status", status));
    }

    private boolean canAccessNonconformity(Nonconformity ncr, InspectionAccessScopeService.InspectionAccess access) {
        if (access.fullDataAccess()) {
            return true;
        }
        if (!StringUtils.hasText(ncr.getInspectionRecordId())) {
            return access.canAccess(ncr.getProcessCode());
        }
        InspectionRecord record = inspectionRecordMapper.selectById(ncr.getInspectionRecordId());
        if (record == null || !TenantContext.tenantCode().equals(record.getTenantCode())) {
            return false;
        }
        InspectionTask task = inspectionTaskMapper.selectById(record.getTaskId());
        return task != null && TenantContext.tenantCode().equals(task.getTenantCode()) && access.canAccess(task.getProcessCode());
    }

    private boolean canAccessCapa(Capa capa, InspectionAccessScopeService.InspectionAccess access) {
        Nonconformity ncr = nonconformityMapper.selectById(capa.getNonconformityId());
        return ncr != null && TenantContext.tenantCode().equals(ncr.getTenantCode()) && canAccessNonconformity(ncr, access);
    }

    private NonconformityResponse toNonconformityResponse(Nonconformity ncr) {
        WorkOrder workOrder = StringUtils.hasText(ncr.getWorkOrderId()) ? workOrderMapper.selectById(ncr.getWorkOrderId()) : null;
        ProductionBatch batch = StringUtils.hasText(ncr.getBatchId()) ? productionBatchMapper.selectById(ncr.getBatchId()) : null;
        return NonconformityResponse.from(ncr, workOrder == null ? "已删除工单" : workOrder.getWorkOrderNo(),
            StringUtils.hasText(ncr.getBatchNo()) ? ncr.getBatchNo() : batch == null ? null : batch.getBatchNo(), detailsFor(ncr));
    }

    private List<NonconformityDetailResponse> detailsFor(Nonconformity ncr) {
        List<NonconformityDetail> details = nonconformityDetailMapper.selectList(Wrappers.lambdaQuery(NonconformityDetail.class)
            .eq(NonconformityDetail::getTenantCode, TenantContext.tenantCode())
            .eq(NonconformityDetail::getNonconformityId, ncr.getId())
            .orderByAsc(NonconformityDetail::getLineNo));
        if (details == null) return List.of();
        return details.stream().map(detail -> {
            WorkOrder workOrder = workOrderMapper.selectById(detail.getWorkOrderId());
            return NonconformityDetailResponse.from(detail, workOrder == null ? "已删除工单" : workOrder.getWorkOrderNo(),
                workOrder == null ? null : workOrder.getInternalProductNo());
        }).toList();
    }

    private CapaResponse toCapaResponse(Capa capa) {
        Nonconformity ncr = nonconformityMapper.selectById(capa.getNonconformityId());
        return CapaResponse.from(capa, ncr == null ? "已删除不良单" : ncr.getNcrNo());
    }

    private String nextCapaNo() {
        long sequence = capaMapper.selectCount(Wrappers.lambdaQuery(Capa.class)
            .eq(Capa::getTenantCode, TenantContext.tenantCode())) + 1;
        return "CAPA-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", sequence);
    }

    private String nextNcrNo() {
        long sequence = nonconformityMapper.selectCount(Wrappers.lambdaQuery(Nonconformity.class)
            .eq(Nonconformity::getTenantCode, TenantContext.tenantCode())) + 1;
        return "NCR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", sequence);
    }
}
