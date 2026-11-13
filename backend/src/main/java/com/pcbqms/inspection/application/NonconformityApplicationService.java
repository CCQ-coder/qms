package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.application.FinishedTestDeviceSummaryService;
import com.pcbqms.inspection.api.CapaResponse;
import com.pcbqms.inspection.api.CloseCapaRequest;
import com.pcbqms.inspection.api.CreateCapaRequest;
import com.pcbqms.inspection.api.CreateNonconformityRequest;
import com.pcbqms.inspection.api.CreateManualNonconformityRequest;
import com.pcbqms.inspection.api.NonconformityDetailResponse;
import com.pcbqms.inspection.api.NonconformityResponse;
import com.pcbqms.inspection.api.UpdateNonconformityDetailRequest;
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
import java.util.HashSet;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
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

    @Transactional
    public void syncDeviceDefects(List<FinishedTestDeviceSummaryService.Summary> summaries) {
        String tenant = TenantContext.tenantCode();
        Set<String> activeGroups = new HashSet<>();
        for (var summary : summaries) {
            if (summary.badCount() <= 0) continue;
            WorkOrder order = workOrderMapper.selectOne(Wrappers.lambdaQuery(WorkOrder.class)
                .eq(WorkOrder::getTenantCode, tenant).eq(WorkOrder::getWorkOrderNo, summary.workOrderNo()));
            if (order == null) continue;
            List<Nonconformity> existing = nonconformityMapper.selectList(Wrappers.lambdaQuery(Nonconformity.class)
                .eq(Nonconformity::getTenantCode, tenant)
                .eq(Nonconformity::getSourceType, "DEVICE")
                .eq(Nonconformity::getWorkOrderId, order.getId())
                .eq(Nonconformity::getDetectionDate, summary.inspectionDate()));
            long reviewed = 0;
            Nonconformity pending = null;
            for (Nonconformity ncr : existing) {
                if ("PENDING_REVIEW".equals(ncr.getStatus())) pending = ncr;
                if (!"APPROVED".equals(ncr.getStatus())) continue;
                var details = nonconformityDetailMapper.selectList(Wrappers.lambdaQuery(NonconformityDetail.class)
                    .eq(NonconformityDetail::getTenantCode, tenant)
                    .eq(NonconformityDetail::getNonconformityId, ncr.getId()));
                reviewed += details.stream().mapToLong(NonconformityDetail::getScrapQuantity).sum();
            }
            long unreviewed = summary.badCount() - reviewed;
            if (unreviewed > Integer.MAX_VALUE) throw new IllegalStateException("设备不良品数量超过处置单可记录范围");
            if (unreviewed <= 0) continue;
            activeGroups.add(summary.inspectionDate() + ":" + order.getId());
            if (pending != null) {
                var details = nonconformityDetailMapper.selectList(Wrappers.lambdaQuery(NonconformityDetail.class)
                    .eq(NonconformityDetail::getTenantCode, tenant)
                    .eq(NonconformityDetail::getNonconformityId, pending.getId()));
                if (details.isEmpty()) throw new IllegalStateException("待审核设备处置单缺少明细：" + pending.getNcrNo());
                if (!Boolean.TRUE.equals(details.getFirst().getManuallyAdjusted())
                    && details.getFirst().getScrapQuantity() != (int) unreviewed) {
                    var detail = details.getFirst();
                    detail.setScrapQuantity((int) unreviewed);
                    detail.setScrapArea(detail.getSinglePcsArea() == null ? null
                        : detail.getSinglePcsArea().multiply(BigDecimal.valueOf(unreviewed)));
                    nonconformityDetailMapper.updateById(detail);
                }
                continue;
            }
            Nonconformity ncr = new Nonconformity();
            ncr.setId(UUID.randomUUID().toString());
            ncr.setTenantCode(tenant);
            ncr.setNcrNo(nextNcrNo());
            ncr.setWorkOrderId(order.getId());
            ncr.setDetectionDate(summary.inspectionDate());
            ncr.setProcessCode("FQC");
            ncr.setProcessName("成品测试");
            ncr.setResponsibleDepartment("测试");
            ncr.setSourceType("DEVICE");
            ncr.setSourceGroupKey(summary.inspectionDate() + ":" + order.getId());
            ncr.setStatus("PENDING_REVIEW");
            ncr.setDispositionMethod("SCRAP");
            ncr.setInitiatedBy("system.device-test");
            ncr.setCreatedAt(LocalDateTime.now());
            nonconformityMapper.insert(ncr);
            NonconformityDetail detail = newDetail(ncr, 1, order, "PCS", (int) unreviewed,
                "设备检测不良品自动汇总", null);
            detail.setStatus("PENDING_REVIEW");
            nonconformityDetailMapper.insert(detail);
        }
        for (Nonconformity pending : nonconformityMapper.selectList(Wrappers.lambdaQuery(Nonconformity.class)
            .eq(Nonconformity::getTenantCode, tenant)
            .eq(Nonconformity::getSourceType, "DEVICE")
            .eq(Nonconformity::getStatus, "PENDING_REVIEW"))) {
            if (activeGroups.contains(pending.getSourceGroupKey())) continue;
            pending.setStatus("CANCELLED");
            pending.setSourceGroupKey(null);
            nonconformityMapper.updateById(pending);
            clearSourceGroupKey(pending);
            updateDetailStatus(pending, "CANCELLED");
        }
    }

    @Transactional
    public NonconformityResponse approveDeviceDisposition(String id, int expectedBadCount) {
        inspectionAccessScopeService.requireSupervisorOrAdministrator();
        if ("customer-b".equals(TenantContext.tenantCode()) && !"宋少帅".equals(TenantContext.actor())) {
            String roleCode = inspectionAccessScopeService.currentAccess().roleCode();
            if (!"PLATFORM_ADMIN".equals(roleCode) && !"TENANT_ADMIN".equals(roleCode)) {
                throw new AccessDeniedException("一级审核仅管理员或宋少帅可操作");
            }
        }
        Nonconformity ncr = requireNcr(id);
        requireNonconformityProcessAccess(ncr);
        if (!isUnreviewed(ncr)) {
            throw new IllegalArgumentException("仅未审核的不良品处置单可以审核");
        }
        int currentBadCount = nonconformityDetailMapper.selectList(Wrappers.lambdaQuery(NonconformityDetail.class)
            .eq(NonconformityDetail::getTenantCode, TenantContext.tenantCode())
            .eq(NonconformityDetail::getNonconformityId, id)).stream()
            .mapToInt(NonconformityDetail::getScrapQuantity).sum();
        if (currentBadCount <= 0 || currentBadCount != expectedBadCount) {
            throw new IllegalArgumentException("处置单不良品数量已变化，请刷新后重新审核");
        }
        ncr.setStatus("APPROVED");
        if ("DEVICE".equals(ncr.getSourceType())) ncr.setSourceGroupKey(null);
        ncr.setReviewedBy(TenantContext.actor());
        ncr.setReviewedAt(LocalDateTime.now());
        nonconformityMapper.updateById(ncr);
        if ("DEVICE".equals(ncr.getSourceType())) clearSourceGroupKey(ncr);
        updateDetailStatus(ncr, "APPROVED");
        return toNonconformityResponse(ncr);
    }

    @Transactional
    public NonconformityResponse updateNonconformity(String id, CreateManualNonconformityRequest request) {
        Nonconformity ncr = requireNcr(id);
        requireNonconformityProcessAccess(ncr);
        requireUnreviewed(ncr);
        inspectionAccessScopeService.requireProcessAccess(request.processCode().trim());
        List<WorkOrder> workOrders = resolveManualWorkOrders(request);
        if ("EXCEL".equals(ncr.getSourceType())) {
            for (int index = 0; index < workOrders.size(); index++) {
                WorkOrder order = workOrders.get(index);
                if (order == null) continue;
                if (!ScrapWorkOrderMatcher.matches(request.details().get(index).internalProductNo(), order.getInternalProductNo())) {
                    throw new IllegalArgumentException("生产指令厂内编号与 Excel 明细不一致");
                }
                if (order.getInputDate() == null || order.getInputDate().isAfter(request.detectionDate())) {
                    throw new IllegalArgumentException("生产指令输入时间缺失或晚于 Excel 表格时间");
                }
            }
        }
        ncr.setWorkOrderId(workOrders.getFirst() == null ? null : workOrders.getFirst().getId());
        ncr.setDetectionDate(request.detectionDate());
        ncr.setShiftName(request.shiftName());
        ncr.setProcessCode(request.processCode().trim());
        ncr.setProcessName(request.processName().trim());
        ncr.setResponsibleDepartment(request.responsibleDepartment());
        ncr.setRemark(request.remark());
        ncr.setDispositionMethod(StringUtils.hasText(request.dispositionMethod()) ? request.dispositionMethod().trim() : "SCRAP");
        ncr.setBatchNo(request.batchNo());
        nonconformityMapper.updateById(ncr);
        nonconformityDetailMapper.delete(Wrappers.lambdaQuery(NonconformityDetail.class)
            .eq(NonconformityDetail::getTenantCode, TenantContext.tenantCode())
            .eq(NonconformityDetail::getNonconformityId, id));
        return NonconformityResponse.from(ncr, workOrders.getFirst() == null ? null : workOrders.getFirst().getWorkOrderNo(),
            ncr.getBatchNo(), saveManualDetails(ncr, request, workOrders));
    }

    @Transactional
    public void deleteNonconformity(String id) {
        Nonconformity ncr = requireNcr(id);
        requireNonconformityProcessAccess(ncr);
        requireUnreviewed(ncr);
        if (capaMapper.selectCount(Wrappers.lambdaQuery(Capa.class)
            .eq(Capa::getTenantCode, TenantContext.tenantCode())
            .eq(Capa::getNonconformityId, id)) > 0) {
            throw new IllegalArgumentException("处置单已关联 CAPA，不能删除");
        }
        nonconformityDetailMapper.delete(Wrappers.lambdaQuery(NonconformityDetail.class)
            .eq(NonconformityDetail::getTenantCode, TenantContext.tenantCode())
            .eq(NonconformityDetail::getNonconformityId, id));
        nonconformityMapper.deleteById(id);
    }

    private boolean isUnreviewed(Nonconformity ncr) {
        return "OPEN".equals(ncr.getStatus()) || "PENDING_REVIEW".equals(ncr.getStatus());
    }

    private void requireUnreviewed(Nonconformity ncr) {
        if (!isUnreviewed(ncr)) throw new IllegalArgumentException("处置单已审核或关闭，不能编辑或删除");
    }

    @Transactional
    public void updateDetail(String ncrId, String detailId, UpdateNonconformityDetailRequest request) {
        Nonconformity ncr = requireNcr(ncrId);
        requireNonconformityProcessAccess(ncr);
        if (!"OPEN".equals(ncr.getStatus()) && !"PENDING_REVIEW".equals(ncr.getStatus())) {
            throw new IllegalArgumentException("处置单已审核或关闭，不能修改明细");
        }
        NonconformityDetail detail = nonconformityDetailMapper.selectById(detailId);
        if (detail == null || !TenantContext.tenantCode().equals(detail.getTenantCode())
            || !ncrId.equals(detail.getNonconformityId())) {
            throw new IllegalArgumentException("不良品明细不存在或无权操作");
        }
        if (!"OPEN".equals(detail.getStatus()) && !"PENDING_REVIEW".equals(detail.getStatus())) {
            throw new IllegalArgumentException("明细已审核或关闭，不能修改");
        }
        if (request.scrapQuantity() == null || request.scrapQuantity() < 1) {
            throw new IllegalArgumentException("报废数量必须大于 0");
        }
        detail.setScrapQuantity(request.scrapQuantity());
        detail.setScrapReason(request.scrapReason());
        detail.setScrapArea(detail.getSinglePcsArea() == null ? null
            : detail.getSinglePcsArea().multiply(BigDecimal.valueOf(request.scrapQuantity())));
        detail.setManuallyAdjusted(true);
        nonconformityDetailMapper.updateById(detail);
    }

    private void clearSourceGroupKey(Nonconformity ncr) {
        nonconformityMapper.update(null, Wrappers.<Nonconformity>update()
            .eq("id", ncr.getId()).eq("tenant_code", ncr.getTenantCode())
            .set("source_group_key", null));
    }

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
        List<WorkOrder> workOrders = resolveManualWorkOrders(request);
        String tenantCode = TenantContext.tenantCode();
        Nonconformity ncr = new Nonconformity();
        ncr.setId(UUID.randomUUID().toString());
        ncr.setTenantCode(tenantCode);
        ncr.setNcrNo(nextNcrNo());
        ncr.setWorkOrderId(workOrders.getFirst() == null ? null : workOrders.getFirst().getId());
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
        return NonconformityResponse.from(ncr, workOrders.getFirst() == null ? null : workOrders.getFirst().getWorkOrderNo(),
            request.batchNo(), saveManualDetails(ncr, request, workOrders));
    }

    private List<WorkOrder> resolveManualWorkOrders(CreateManualNonconformityRequest request) {
        String tenantCode = TenantContext.tenantCode();
        List<WorkOrder> workOrders = new ArrayList<>();
        for (var line : request.details()) {
            WorkOrder workOrder = null;
            if (StringUtils.hasText(line.workOrderNo())) {
                workOrder = workOrderMapper.selectOne(Wrappers.lambdaQuery(WorkOrder.class)
                    .eq(WorkOrder::getTenantCode, tenantCode)
                    .eq(WorkOrder::getWorkOrderNo, line.workOrderNo().trim()));
                if (workOrder == null || !tenantCode.equals(workOrder.getTenantCode())) {
                    throw new IllegalArgumentException("生产指令不存在或无权操作：" + line.workOrderNo());
                }
            } else if (!StringUtils.hasText(line.internalProductNo())) {
                throw new IllegalArgumentException("未选择生产指令时必须填写厂内编号");
            }
            if (line.layerCount() != null && line.layerCount() < 1
                || line.singlePcsArea() != null && line.singlePcsArea().signum() < 0
                || line.scrapArea() != null && line.scrapArea().signum() < 0) {
                throw new IllegalArgumentException("层数和面积不能为负，层数须大于零");
            }
            workOrders.add(workOrder);
        }
        return workOrders;
    }

    private List<NonconformityDetailResponse> saveManualDetails(Nonconformity ncr, CreateManualNonconformityRequest request,
                                                                 List<WorkOrder> workOrders) {
        List<NonconformityDetailResponse> responses = new ArrayList<>();
        for (int index = 0; index < request.details().size(); index++) {
            var line = request.details().get(index);
            WorkOrder workOrder = workOrders.get(index);
            NonconformityDetail detail = newDetail(ncr, index + 1, workOrder, line.unit(), line.scrapQuantity(),
                line.scrapReason(), line.remark());
            detail.setInternalProductNo(StringUtils.hasText(line.internalProductNo())
                ? line.internalProductNo().trim() : workOrder == null ? null : workOrder.getInternalProductNo());
            detail.setMaterialNo(StringUtils.hasText(line.materialNo())
                ? line.materialNo().trim() : workOrder == null ? null : workOrder.getMaterialNo());
            detail.setLayerCount(line.layerCount() != null ? line.layerCount()
                : workOrder == null ? null : workOrder.getLayerCount());
            if (line.singlePcsArea() != null) detail.setSinglePcsArea(line.singlePcsArea());
            detail.setScrapArea(line.scrapArea() != null ? line.scrapArea()
                : detail.getSinglePcsArea() == null ? null
                    : detail.getSinglePcsArea().multiply(BigDecimal.valueOf(line.scrapQuantity())));
            detail.setStatus(ncr.getStatus());
            detail.setManuallyAdjusted(true);
            nonconformityDetailMapper.insert(detail);
            responses.add(NonconformityDetailResponse.from(detail, workOrder));
        }
        return responses;
    }

    @Transactional
    public boolean createImportedScrapRow(String documentId, String importer,
                                           ScrapExcelImportParser.ScrapRow row, WorkOrder order) {
        String tenant = TenantContext.tenantCode();
        String sourceKey = "scrap:" + documentId + ":" + row.rowNumber();
        if (nonconformityMapper.selectCount(Wrappers.lambdaQuery(Nonconformity.class)
            .eq(Nonconformity::getTenantCode, tenant).eq(Nonconformity::getSourceGroupKey, sourceKey)) > 0) return false;
        Nonconformity ncr = new Nonconformity();
        ncr.setId(UUID.randomUUID().toString());
        ncr.setTenantCode(tenant);
        ncr.setNcrNo(nextNcrNo());
        ncr.setWorkOrderId(order == null ? null : order.getId());
        ncr.setStatus("OPEN");
        ncr.setSourceType("EXCEL");
        ncr.setSourceGroupKey(sourceKey);
        ncr.setDetectionDate(row.date());
        ncr.setProcessCode("FQC");
        ncr.setProcessName(row.station());
        ncr.setResponsibleDepartment(row.department());
        ncr.setDispositionMethod("SCRAP");
        ncr.setInitiatedBy(importer);
        ncr.setCreatedAt(LocalDateTime.now());
        nonconformityMapper.insert(ncr);

        NonconformityDetail detail = new NonconformityDetail();
        detail.setId(UUID.randomUUID().toString());
        detail.setTenantCode(tenant);
        detail.setNonconformityId(ncr.getId());
        detail.setLineNo(1);
        detail.setWorkOrderId(order == null ? null : order.getId());
        detail.setInternalProductNo(row.internalProductNo());
        detail.setLayerCount(row.layerCount());
        detail.setProcessCode("FQC");
        detail.setProcessName(row.station());
        detail.setUnit("PCS");
        detail.setSinglePcsArea(row.singlePcsArea());
        detail.setScrapQuantity(row.quantity());
        detail.setScrapArea(row.scrapArea());
        detail.setScrapReason(row.scrapReason());
        detail.setRemark(row.remark());
        detail.setStatus("OPEN");
        nonconformityDetailMapper.insert(detail);
        return true;
    }

    @Transactional
    public void bindImportedScrapRow(String ncrId, String detailId, String requestedWorkOrderNo) {
        Nonconformity ncr = requireNcr(ncrId);
        requireNonconformityProcessAccess(ncr);
        if (!"EXCEL".equals(ncr.getSourceType()) || !"OPEN".equals(ncr.getStatus())) {
            throw new IllegalArgumentException("仅未审核的 Excel 导入明细可手工关联");
        }
        NonconformityDetail detail = nonconformityDetailMapper.selectById(detailId);
        if (detail == null || !TenantContext.tenantCode().equals(detail.getTenantCode())
            || !ncrId.equals(detail.getNonconformityId()) || detail.getWorkOrderId() != null) {
            throw new IllegalArgumentException("明细不存在、已关联或无权操作");
        }
        if (!StringUtils.hasText(requestedWorkOrderNo)) throw new IllegalArgumentException("请选择生产指令");
        WorkOrder order = workOrderMapper.selectOne(Wrappers.lambdaQuery(WorkOrder.class)
            .eq(WorkOrder::getTenantCode, TenantContext.tenantCode())
            .eq(WorkOrder::getWorkOrderNo, requestedWorkOrderNo.trim()));
        if (order == null || !TenantContext.tenantCode().equals(order.getTenantCode())) {
            throw new IllegalArgumentException("当前租户不存在此生产指令");
        }
        if (!ScrapWorkOrderMatcher.matches(detail.getInternalProductNo(), order.getInternalProductNo())) {
            throw new IllegalArgumentException("生产指令厂内编号与 Excel 明细不一致");
        }
        if (order.getInputDate() == null || order.getInputDate().isAfter(ncr.getDetectionDate())) {
            throw new IllegalArgumentException("生产指令输入时间缺失或晚于 Excel 表格时间");
        }
        detail.setWorkOrderId(order.getId());
        ncr.setWorkOrderId(order.getId());
        nonconformityDetailMapper.updateById(detail);
        nonconformityMapper.updateById(ncr);
    }

    private NonconformityDetail newDetail(Nonconformity ncr, int lineNo, WorkOrder workOrder, String unit,
                                          int scrapQuantity, String scrapReason, String remark) {
        NonconformityDetail detail = new NonconformityDetail();
        detail.setId(UUID.randomUUID().toString());
        detail.setTenantCode(ncr.getTenantCode());
        detail.setNonconformityId(ncr.getId());
        detail.setLineNo(lineNo);
        detail.setWorkOrderId(workOrder == null ? null : workOrder.getId());
        detail.setProcessCode(ncr.getProcessCode());
        detail.setProcessName(ncr.getProcessName());
        detail.setUnit(StringUtils.hasText(unit) ? unit.trim() : "PCS");
        detail.setSinglePcsArea(workOrder == null ? null : workOrder.getSinglePcsArea());
        detail.setScrapQuantity(scrapQuantity);
        detail.setScrapArea(workOrder == null || workOrder.getSinglePcsArea() == null ? null
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
        return NonconformityResponse.from(ncr, workOrder == null
                ? (ncr.getWorkOrderId() == null ? null : "已删除工单") : workOrder.getWorkOrderNo(),
            StringUtils.hasText(ncr.getBatchNo()) ? ncr.getBatchNo() : batch == null ? null : batch.getBatchNo(), detailsFor(ncr));
    }

    private List<NonconformityDetailResponse> detailsFor(Nonconformity ncr) {
        List<NonconformityDetail> details = nonconformityDetailMapper.selectList(Wrappers.lambdaQuery(NonconformityDetail.class)
            .eq(NonconformityDetail::getTenantCode, TenantContext.tenantCode())
            .eq(NonconformityDetail::getNonconformityId, ncr.getId())
            .orderByAsc(NonconformityDetail::getLineNo));
        if (details == null) return List.of();
        return details.stream().map(detail -> {
            WorkOrder workOrder = detail.getWorkOrderId() == null ? null : workOrderMapper.selectById(detail.getWorkOrderId());
            return NonconformityDetailResponse.from(detail, workOrder);
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
        String prefix = "NCR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        List<Nonconformity> latest = nonconformityMapper.selectList(Wrappers.lambdaQuery(Nonconformity.class)
            .eq(Nonconformity::getTenantCode, TenantContext.tenantCode())
            .likeRight(Nonconformity::getNcrNo, prefix)
            .orderByDesc(Nonconformity::getNcrNo).last("LIMIT 1"));
        long sequence = latest.isEmpty() ? 1 : Long.parseLong(latest.getFirst().getNcrNo().substring(prefix.length())) + 1;
        return prefix + String.format("%04d", sequence);
    }
}
