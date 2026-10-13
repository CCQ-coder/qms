package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.api.CapaResponse;
import com.pcbqms.inspection.api.CloseCapaRequest;
import com.pcbqms.inspection.api.CreateCapaRequest;
import com.pcbqms.inspection.api.CreateNonconformityRequest;
import com.pcbqms.inspection.api.NonconformityResponse;
import com.pcbqms.inspection.domain.Capa;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.Nonconformity;
import com.pcbqms.inspection.infrastructure.persistence.CapaMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.NonconformityMapper;
import com.pcbqms.workorder.domain.ProductionBatch;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        ncr.setInitiatedBy(TenantContext.actor());
        ncr.setCreatedAt(now);
        nonconformityMapper.insert(ncr);
        WorkOrder workOrder = workOrderMapper.selectById(task.getWorkOrderId());
        ProductionBatch batch = StringUtils.hasText(task.getBatchId()) ? productionBatchMapper.selectById(task.getBatchId()) : null;
        return NonconformityResponse.from(ncr, workOrder == null ? "已删除工单" : workOrder.getWorkOrderNo(),
            batch == null ? null : batch.getBatchNo());
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

    private boolean canAccessNonconformity(Nonconformity ncr, InspectionAccessScopeService.InspectionAccess access) {
        if (access.fullDataAccess()) {
            return true;
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
        WorkOrder workOrder = workOrderMapper.selectById(ncr.getWorkOrderId());
        ProductionBatch batch = StringUtils.hasText(ncr.getBatchId()) ? productionBatchMapper.selectById(ncr.getBatchId()) : null;
        return NonconformityResponse.from(ncr, workOrder == null ? "已删除工单" : workOrder.getWorkOrderNo(),
            batch == null ? null : batch.getBatchNo());
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
