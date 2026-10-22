package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.inspection.api.CreateInspectionRecordRequest;
import com.pcbqms.inspection.domain.FlyingProbeProjection;
import com.pcbqms.inspection.domain.FlyingProbeRun;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.InspectionTemplate;
import com.pcbqms.inspection.infrastructure.persistence.FlyingProbeProjectionMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTemplateMapper;
import com.pcbqms.workorder.domain.ProductionBatch;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FlyingProbeInspectionProjectionService {
    static final String EVENT_TYPE = "FLYING_PROBE_TEST_COUNTERS";
    private static final String TEMPLATE_CODE = "FLYING_PROBE_ELECTRICAL_V1";
    private static final String ADMIN = "admin";
    private final FlyingProbeProjectionMapper projectionMapper;
    private final EquipmentMapper equipmentMapper;
    private final WorkOrderMapper workOrderMapper;
    private final ProductionBatchMapper productionBatchMapper;
    private final InspectionTemplateMapper templateMapper;
    private final InspectionTaskMapper taskMapper;
    private final InspectionApplicationService inspectionApplicationService;
    private final InspectionTemplateApplicationService templateApplicationService;
    private final InspectionAccessScopeService accessScopeService;
    private final ObjectMapper objectMapper;

    @Value("${qms.equipment-telemetry.retention.raw-days:7}")
    private int rawRetentionDays = 7;

    @Transactional
    public void projectNew(EquipmentTelemetryRecord telemetry) {
        if (!EVENT_TYPE.equals(telemetry.getEventType())) {
            return;
        }
        projectionMapper.claim(telemetry);
        FlyingProbeProjection projection = projectionMapper.lockProjection(telemetry.getId());
        if (projection != null && "PENDING".equals(projection.getStatus())) {
            process(projection);
        }
    }

    @Transactional
    public void retry(FlyingProbeProjection pending) {
        FlyingProbeProjection projection = projectionMapper.lockProjection(pending.getTelemetryId());
        if (projection != null && "RETRYABLE".equals(projection.getStatus())
            && !projection.getRetryAfter().isAfter(LocalDateTime.now())) {
            process(projection);
        }
    }

    /** Called after the projection transaction has rolled back, so a bad row cannot block the next batch. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnexpectedFailure(EquipmentTelemetryRecord telemetry) {
        if (!EVENT_TYPE.equals(telemetry.getEventType())
            || !TenantContext.tenantCode().equals(telemetry.getTenantCode())) {
            return;
        }
        projectionMapper.claim(telemetry);
        FlyingProbeProjection projection = projectionMapper.lockProjection(telemetry.getId());
        if (projection != null && "PENDING".equals(projection.getStatus())) {
            markFailure(projection);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryFailure(FlyingProbeProjection pending) {
        FlyingProbeProjection projection = projectionMapper.lockProjection(pending.getTelemetryId());
        if (projection != null && TenantContext.tenantCode().equals(projection.getTenantCode())
            && "RETRYABLE".equals(projection.getStatus())) {
            markFailure(projection);
        }
    }

    private void markFailure(FlyingProbeProjection projection) {
        LocalDateTime now = LocalDateTime.now();
        projection.setStatus("RETRYABLE");
        projection.setReason("飞针投影发生内部异常，稍后重试；详见服务日志");
        projection.setRetryAfter(now.plusMinutes(5));
        projection.setProcessedAt(now);
        projectionMapper.updateProjection(projection);
    }

    private void process(FlyingProbeProjection projection) {
        if (!TenantContext.tenantCode().equals(projection.getTenantCode()) || !ADMIN.equals(TenantContext.actor())) {
            throw new IllegalStateException("飞针投影必须使用所在租户的 admin 身份");
        }
        LocalDateTime now = LocalDateTime.now();
        Snapshot snapshot;
        try {
            snapshot = parse(projection);
        } catch (IllegalArgumentException exception) {
            skip(projection, exception.getMessage(), now);
            return;
        }
        projection.setSourceFile(snapshot.sourceFile());
        projection.setSourceStartTime(snapshot.startTime());
        projection.setRunKey(runKey(projection.getTenantCode(), projection.getEquipmentId(), snapshot));
        projection.setCumulativeTested(snapshot.counts().tested());
        projection.setCumulativeGood(snapshot.counts().good());
        projection.setCumulativeBad(snapshot.counts().bad());

        Equipment equipment = equipmentMapper.selectById(projection.getEquipmentId());
        if (equipment == null || !projection.getTenantCode().equals(equipment.getTenantCode())
            || !"成品测试设备".equals(equipment.getEquipmentType())) {
            retryLater(projection, "设备台账不是当前租户的成品测试设备", now);
            return;
        }
        if (projectionMapper.countEarlierUnresolved(projection) > 0) {
            retryLater(projection, "同一飞针文件的更早快照仍待匹配", now);
            return;
        }
        String materialKey = normalizeMaterialKey(projection.getMaterialNo());
        FlyingProbeRun run = projectionMapper.lockRun(projection.getRunKey());
        WorkOrder workOrder;
        if (run == null) {
            List<WorkOrder> candidates = workOrderMapper.selectList(Wrappers.lambdaQuery(WorkOrder.class)
                    .eq(WorkOrder::getTenantCode, projection.getTenantCode())
                    .apply("internal_product_no_match_key = {0}", materialKey))
                .stream().filter(order -> materialKey.equals(normalizeMaterialKey(order.getInternalProductNo())))
                .toList();
            if (candidates.size() != 1) {
                retryLater(projection, "厂内编号精确匹配生产指令数量为 " + candidates.size() + "，必须恰好为 1", now);
                return;
            }
            workOrder = candidates.getFirst();
        } else {
            if (!"ACTIVE".equals(run.getStatus())) {
                skip(projection, "文件批次已因计数回退停止自动投影：" + run.getReason(), now);
                return;
            }
            if (!projection.getTenantCode().equals(run.getTenantCode())
                || !projection.getEquipmentId().equals(run.getEquipmentId())
                || !materialKey.equals(normalizeMaterialKey(run.getMaterialNo()))) {
                retryLater(projection, "文件批次来源或工单映射发生变化，需人工核对", now);
                return;
            }
            workOrder = workOrderMapper.selectOne(Wrappers.lambdaQuery(WorkOrder.class)
                .eq(WorkOrder::getTenantCode, projection.getTenantCode())
                .eq(WorkOrder::getWorkOrderNo, run.getWorkOrderNo()));
            if (workOrder == null
                || !materialKey.equals(normalizeMaterialKey(workOrder.getInternalProductNo()))) {
                retryLater(projection, "文件批次工单映射发生变化，需人工核对", now);
                return;
            }
        }
        List<ProductionBatch> batches = productionBatchMapper.selectList(Wrappers.lambdaQuery(ProductionBatch.class)
            .eq(ProductionBatch::getTenantCode, projection.getTenantCode())
            .eq(ProductionBatch::getWorkOrderId, workOrder.getId())
            .eq(ProductionBatch::getStatus, "ACTIVE"));
        ProductionBatch batch = batches.size() == 1 ? batches.getFirst() : null;
        InspectionTemplate template = templateMapper.selectOne(Wrappers.lambdaQuery(InspectionTemplate.class)
            .eq(InspectionTemplate::getTenantCode, projection.getTenantCode())
            .eq(InspectionTemplate::getTemplateCode, TEMPLATE_CODE)
            .eq(InspectionTemplate::getStatus, "ACTIVE"));
        if (template == null || !"FQC".equals(template.getProcessCode())
            || !"FINISHED".equals(template.getTaskType())) {
            retryLater(projection, "专用飞针电测检验模板未启用或配置错误", now);
            return;
        }

        if (run == null) {
            FlyingProbeRun newRun = new FlyingProbeRun();
            newRun.setRunKey(projection.getRunKey());
            newRun.setTenantCode(projection.getTenantCode());
            newRun.setEquipmentId(projection.getEquipmentId());
            newRun.setSourceFile(snapshot.sourceFile());
            newRun.setSourceStartTime(snapshot.startTime());
            newRun.setMaterialNo(projection.getMaterialNo());
            newRun.setWorkOrderNo(workOrder.getWorkOrderNo());
            newRun.setUpdatedAt(now);
            projectionMapper.insertRunIfAbsent(newRun);
            run = projectionMapper.lockRun(projection.getRunKey());
        }
        if (!"ACTIVE".equals(run.getStatus())) {
            skip(projection, "文件批次已因计数回退停止自动投影：" + run.getReason(), now);
            return;
        }
        if (!projection.getTenantCode().equals(run.getTenantCode())
            || !projection.getEquipmentId().equals(run.getEquipmentId())
            || !materialKey.equals(normalizeMaterialKey(run.getMaterialNo()))
            || !workOrder.getWorkOrderNo().equals(run.getWorkOrderNo())) {
            retryLater(projection, "文件批次来源或工单映射发生变化，需人工核对", now);
            return;
        }
        projection.setWorkOrderNo(workOrder.getWorkOrderNo());
        projection.setBatchNo(batch == null ? null : batch.getBatchNo());
        // The first retained sample of an old file is only a baseline. Its earlier
        // increments may have expired from raw telemetry, so their quantity is unknown.
        if (run.getLastDeviceTime() == null && snapshot.startTime().isBefore(now.minusDays(rawRetentionDays))) {
            run.setLastDeviceTime(projection.getDeviceTime());
            run.setTestedCount(snapshot.counts().tested());
            run.setGoodCount(snapshot.counts().good());
            run.setBadCount(snapshot.counts().bad());
            run.setUpdatedAt(now);
            projectionMapper.updateRun(run);
            projection.setDeltaTested(0);
            projection.setDeltaGood(0);
            projection.setDeltaBad(0);
            projection.setStatus("PROCESSED");
            projection.setReason("文件开始时间早于原始遥测保留期，首条快照仅建立基线");
            projection.setRetryAfter(null);
            projection.setProcessedAt(now);
            linkSource(projection);
            projectionMapper.updateProjection(projection);
            return;
        }
        if (run.getLastDeviceTime() != null && projection.getDeviceTime().isBefore(run.getLastDeviceTime())) {
            skip(projection, "旧快照晚到，已由更新的累计快照覆盖", now);
            return;
        }
        FlyingProbeCounts previous = new FlyingProbeCounts(
            run.getTestedCount(), run.getGoodCount(), run.getBadCount());
        FlyingProbeCounts delta;
        try {
            delta = snapshot.counts().deltaFrom(previous);
        } catch (IllegalArgumentException exception) {
            if (run.getLastDeviceTime() != null && projection.getDeviceTime().equals(run.getLastDeviceTime())) {
                skip(projection, "同一设备时间的旧快照或矛盾计数", now);
            } else {
                run.setStatus("BLOCKED_RESET");
                run.setReason(exception.getMessage());
                run.setUpdatedAt(now);
                projectionMapper.updateRun(run);
                skip(projection, exception.getMessage() + "，文件批次已停止自动投影", now);
            }
            return;
        }
        projection.setDeltaTested(delta.tested());
        projection.setDeltaGood(delta.good());
        projection.setDeltaBad(delta.bad());
        if (delta.tested() > 0) {
            Map<String, Object> results = Map.of(
                "inspectionQuantity", delta.tested(),
                "testedBoardCount", delta.tested(),
                "goodBoardCount", delta.good(),
                "badBoardCount", delta.bad(),
                "sourceProtocol", "FLYING_PROBE_TXT_V1",
                "sourceFile", snapshot.sourceFile(),
                "sourceStartTime", snapshot.startTime().toString(),
                "sourceTelemetryId", projection.getTelemetryId(),
                "sourceRecordNo", projection.getSourceRecordNo() == null ? "" : projection.getSourceRecordNo(),
                "sourceDeviceTime", projection.getDeviceTime().toString());
            String verdict = delta.bad() == 0 ? "PASS" : "FAIL";
            try {
                accessScopeService.requireProcessAccess("FQC");
                templateApplicationService.validateRecordResults(template.getId(), results, verdict);
            } catch (RuntimeException exception) {
                retryLater(projection, "admin 权限或飞针模板校验失败：" + exception.getMessage(), now);
                return;
            }
            InspectionTask task = new InspectionTask();
            task.setId(UUID.randomUUID().toString());
            task.setTenantCode(projection.getTenantCode());
            task.setTaskNo("IT-FP-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
            task.setWorkOrderId(workOrder.getId());
            task.setBatchId(batch == null ? null : batch.getId());
            task.setProcessCode("FQC");
            task.setProcessName("成品飞针电测");
            task.setTaskType("FINISHED");
            task.setTemplateId(template.getId());
            task.setSourceType("AUTO");
            task.setStatus("PENDING");
            task.setAssigneeName(ADMIN);
            task.setCreatedBy("system.flying-probe");
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            taskMapper.insert(task);
            String defectSummary = delta.bad() == 0 ? null : "飞针电测新增坏板 " + delta.bad() + " 块";
            var submitted = inspectionApplicationService.submitAutomaticRecord(task.getId(),
                new CreateInspectionRecordRequest(verdict, delta.bad(), defectSummary, null, results,
                    List.of(), equipment.getAssetNo(), "FP-" + projection.getTelemetryId()), projection.getDeviceTime());
            projection.setInspectionTaskId(task.getId());
            projection.setInspectionRecordId(submitted.id());
        }
        run.setLastDeviceTime(projection.getDeviceTime());
        run.setTestedCount(snapshot.counts().tested());
        run.setGoodCount(snapshot.counts().good());
        run.setBadCount(snapshot.counts().bad());
        run.setUpdatedAt(now);
        projectionMapper.updateRun(run);
        projection.setStatus("PROCESSED");
        projection.setReason(null);
        projection.setRetryAfter(null);
        projection.setProcessedAt(now);
        linkSource(projection);
        projectionMapper.updateProjection(projection);
    }

    private void linkSource(FlyingProbeProjection projection) {
        projectionMapper.linkTelemetry(projection);
        if (StringUtils.hasText(projection.getSourceRecordNo())) {
            projectionMapper.linkCurrentRuntime(projection);
        }
    }

    private Snapshot parse(FlyingProbeProjection projection) {
        if (!StringUtils.hasText(projection.getMaterialNo())) {
            throw new IllegalArgumentException("飞针文件缺少料号");
        }
        if (projection.getDeviceTime() == null || projection.getReceivedAt() == null
            || projection.getDeviceTime().isAfter(projection.getReceivedAt().plusDays(1))) {
            throw new IllegalArgumentException("设备检测时间无效或明显晚于 QMS 接收时间");
        }
        try {
            JsonNode attributes = objectMapper.readTree(projection.getRawPayload()).path("attributes");
            JsonNode file = attributes.path("sourceFile");
            JsonNode start = attributes.path("startTime");
            if (!file.isTextual() || !StringUtils.hasText(file.textValue()) || file.textValue().length() > 512
                || !start.isTextual()) {
                throw new IllegalArgumentException("飞针来源文件或开始时间缺失");
            }
            FlyingProbeCounts counts = new FlyingProbeCounts(
                count(attributes, "testedBoardCount"), count(attributes, "goodBoardCount"),
                count(attributes, "badBoardCount"));
            counts.deltaFrom(FlyingProbeCounts.ZERO);
            LocalDateTime startedAt = LocalDateTime.parse(start.textValue());
            if (startedAt.isAfter(projection.getDeviceTime())) {
                throw new IllegalArgumentException("飞针文件开始时间晚于设备检测时间");
            }
            return new Snapshot(file.textValue(), startedAt, counts);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("飞针来源或计数格式无效", exception);
        }
    }

    private int count(JsonNode attributes, String name) {
        JsonNode value = attributes.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("飞针计数字段无效：" + name);
        }
        return value.intValue();
    }

    private String runKey(String tenantCode, String equipmentId, Snapshot snapshot) {
        try {
            String identity = tenantCode + "\n" + equipmentId + "\n"
                + snapshot.sourceFile().toUpperCase(Locale.ROOT) + "\n" + snapshot.startTime();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String normalizeMaterialKey(String materialNo) {
        return StringUtils.hasText(materialNo) ? materialNo.trim().toUpperCase(Locale.ROOT) : null;
    }

    private void skip(FlyingProbeProjection projection, String reason, LocalDateTime now) {
        projection.setStatus("SKIPPED");
        projection.setReason(reason);
        projection.setRetryAfter(null);
        projection.setProcessedAt(now);
        projectionMapper.updateProjection(projection);
    }

    private void retryLater(FlyingProbeProjection projection, String reason, LocalDateTime now) {
        projection.setStatus("RETRYABLE");
        projection.setReason(reason);
        projection.setRetryAfter(now.plusMinutes(5));
        projection.setProcessedAt(now);
        projectionMapper.updateProjection(projection);
    }

    private record Snapshot(String sourceFile, LocalDateTime startTime, FlyingProbeCounts counts) { }
}
