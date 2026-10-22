package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.inspection.api.CreateInspectionRecordRequest;
import com.pcbqms.inspection.api.InspectionRecordResponse;
import com.pcbqms.inspection.domain.FlyingProbeProjection;
import com.pcbqms.inspection.domain.FlyingProbeRun;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.InspectionTemplate;
import com.pcbqms.inspection.infrastructure.persistence.FlyingProbeProjectionMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTemplateMapper;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlyingProbeInspectionProjectionServiceTest {
    @Mock private FlyingProbeProjectionMapper projectionMapper;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private ProductionBatchMapper productionBatchMapper;
    @Mock private InspectionTemplateMapper templateMapper;
    @Mock private InspectionTaskMapper taskMapper;
    @Mock private InspectionApplicationService inspectionApplicationService;
    @Mock private InspectionTemplateApplicationService templateApplicationService;
    @Mock private InspectionAccessScopeService accessScopeService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createsOnlyNewBoardsAndFailsOnlyWhenNewBadBoardsAppear() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var start = now.minusHours(1);
        var first = event("first", start, now.minusMinutes(2), 3, 3, 0);
        var second = event("second", start, now.minusMinutes(1), 5, 4, 1);
        Map<String, FlyingProbeProjection> projections = Map.of(
            "first", projection(first), "second", projection(second));
        when(projectionMapper.lockProjection(any())).thenAnswer(call -> projections.get(call.getArgument(0)));
        validEquipmentAndTemplate();
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder()));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        FlyingProbeRun run = run();
        when(projectionMapper.lockRun(any())).thenReturn(null, run, run);
        when(workOrderMapper.selectOne(any())).thenReturn(workOrder());
        when(inspectionApplicationService.submitAutomaticRecord(any(), any(), any())).thenAnswer(call ->
            new InspectionRecordResponse(call.getArgument(0), call.getArgument(0),
                ((CreateInspectionRecordRequest) call.getArgument(1)).verdict(), 0, null, null, null,
                call.getArgument(2)));
        var service = service();

        service.projectNew(first);
        service.projectNew(second);
        service.projectNew(first);

        ArgumentCaptor<CreateInspectionRecordRequest> requests = ArgumentCaptor.forClass(CreateInspectionRecordRequest.class);
        verify(inspectionApplicationService, times(2)).submitAutomaticRecord(any(), requests.capture(), any());
        assertThat(requests.getAllValues()).extracting(request -> request.results().get("inspectionQuantity"))
            .containsExactly(3, 2);
        assertThat(requests.getAllValues()).extracting(CreateInspectionRecordRequest::verdict)
            .containsExactly("PASS", "FAIL");
        assertThat(requests.getAllValues()).extracting(CreateInspectionRecordRequest::defectiveQuantity)
            .containsExactly(0, 1);
        assertThat(run.getTestedCount()).isEqualTo(5);
        assertThat(projections.get("first").getStatus()).isEqualTo("PROCESSED");
        assertThat(projections.get("second").getStatus()).isEqualTo("PROCESSED");
        verify(taskMapper, times(2)).insert(any(InspectionTask.class));
    }

    @Test
    void ambiguousWorkOrderIsRetryableAndDoesNotCreateARecord() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("ambiguous", now.minusHours(1), now.minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("ambiguous")).thenReturn(projection);
        validEquipment();
        WorkOrder secondOrder = workOrder();
        secondOrder.setId("work-2");
        secondOrder.setWorkOrderNo("WO-2");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(), secondOrder));

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
        assertThat(projection.getReason()).contains("必须恰好为 1");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
        verify(projectionMapper, never()).linkTelemetry(any());
    }

    @Test
    void matchesInternalProductNumberIgnoringCaseAndOuterSpaces() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("normalized", now.minusHours(1), now.minusMinutes(1), 3, 3, 0);
        telemetry.setMaterialNo(" p2l046907a ");
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("normalized")).thenReturn(projection);
        validEquipment();
        WorkOrder order = workOrder();
        order.setInternalProductNo("P2L046907A");
        order.setCustomerPartNo("CUSTOMER-PART-OTHER");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(order));

        service().projectNew(telemetry);

        assertThat(projection.getReason()).contains("专用飞针电测检验模板");
    }

    @Test
    void caseAndSpaceVariantsOfTwoOrdersRemainAmbiguous() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("variant-ambiguity", now.minusHours(1), now.minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("variant-ambiguity")).thenReturn(projection);
        validEquipment();
        WorkOrder secondOrder = workOrder();
        secondOrder.setId("work-2");
        secondOrder.setWorkOrderNo("WO-2");
        secondOrder.setInternalProductNo(" part-1 ");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder(), secondOrder));

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
        assertThat(projection.getReason()).contains("数量为 2");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void productCodeAloneCannotMatchANewRun() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("product-only", now.minusHours(1), now.minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("product-only")).thenReturn(projection);
        validEquipment();
        WorkOrder order = workOrder();
        order.setInternalProductNo("OTHER-PART");
        order.setProductCode("PART-1");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(order));

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
        assertThat(projection.getReason()).contains("数量为 0");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void alreadyBoundRunKeepsItsWorkOrderWhenAnotherOrderWithSameInternalNumberAppears() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("bound", now.minusHours(1), now.minusMinutes(1), 5, 4, 1);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("bound")).thenReturn(projection);
        validEquipmentAndTemplate();
        FlyingProbeRun boundRun = run();
        boundRun.setMaterialNo(" part-1 ");
        boundRun.setLastDeviceTime(now.minusMinutes(2));
        boundRun.setTestedCount(3);
        boundRun.setGoodCount(3);
        when(projectionMapper.lockRun(any())).thenReturn(boundRun);
        when(workOrderMapper.selectOne(any())).thenReturn(workOrder());
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        when(inspectionApplicationService.submitAutomaticRecord(any(), any(), any())).thenAnswer(call ->
            new InspectionRecordResponse("record-bound", call.getArgument(0), "FAIL", 1, null, null,
                null, call.getArgument(2)));

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("WO-1");
        assertThat(projection.getDeltaTested()).isEqualTo(2);
        verify(workOrderMapper, never()).selectList(any());
        verify(taskMapper).insert(any(InspectionTask.class));
    }

    @Test
    void alreadyBoundRunStopsIfItsPinnedOrderNoLongerMatchesInternalNumber() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("changed-order", now.minusHours(1), now.minusMinutes(1), 5, 4, 1);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("changed-order")).thenReturn(projection);
        validEquipment();
        when(projectionMapper.lockRun(any())).thenReturn(run());
        WorkOrder changed = workOrder();
        changed.setInternalProductNo("OTHER-PART");
        when(workOrderMapper.selectOne(any())).thenReturn(changed);

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
        assertThat(projection.getReason()).contains("工单映射");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void oldFileFirstRetainedSnapshotOnlyEstablishesBaseline() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("old", now.minusDays(8), now.minusMinutes(1), 20, 18, 2);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("old")).thenReturn(projection);
        validEquipmentAndTemplate();
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder()));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        FlyingProbeRun run = run();
        when(projectionMapper.lockRun(any())).thenReturn(null, run);

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getReason()).contains("仅建立基线");
        assertThat(run.getTestedCount()).isEqualTo(20);
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void cumulativeCountRollbackStopsRunWithoutNewRecord() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("reset", now.minusHours(1), now.minusMinutes(1), 4, 3, 1);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("reset")).thenReturn(projection);
        validEquipmentAndTemplate();
        when(workOrderMapper.selectOne(any())).thenReturn(workOrder());
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        FlyingProbeRun run = run();
        run.setLastDeviceTime(now.minusMinutes(2));
        run.setTestedCount(5);
        run.setGoodCount(4);
        run.setBadCount(1);
        when(projectionMapper.lockRun(any())).thenReturn(run);

        service().projectNew(telemetry);

        assertThat(run.getStatus()).isEqualTo("BLOCKED_RESET");
        assertThat(projection.getStatus()).isEqualTo("SKIPPED");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void unexpectedProjectionErrorIsPersistedForLaterRetryWithoutExposingPayload() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("crashed", now.minusHours(1), now.minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("crashed")).thenReturn(projection);

        service().markUnexpectedFailure(telemetry);

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
        assertThat(projection.getRetryAfter()).isAfter(now);
        assertThat(projection.getReason()).contains("服务日志").doesNotContain("run.txt", "PART-1");
        verify(projectionMapper).updateProjection(projection);
    }

    private FlyingProbeInspectionProjectionService service() {
        return new FlyingProbeInspectionProjectionService(projectionMapper, equipmentMapper, workOrderMapper,
            productionBatchMapper, templateMapper, taskMapper, inspectionApplicationService,
            templateApplicationService, accessScopeService, new ObjectMapper());
    }

    private void validEquipmentAndTemplate() {
        validEquipment();
        InspectionTemplate template = new InspectionTemplate();
        template.setId("template-1");
        template.setProcessCode("FQC");
        template.setTaskType("FINISHED");
        when(templateMapper.selectOne(any())).thenReturn(template);
    }

    private void validEquipment() {
        Equipment equipment = new Equipment();
        equipment.setTenantCode("tenant-a");
        equipment.setEquipmentType("成品测试设备");
        equipment.setAssetNo("FPT-1");
        when(equipmentMapper.selectById("equipment-1")).thenReturn(equipment);
    }

    private WorkOrder workOrder() {
        WorkOrder order = new WorkOrder();
        order.setId("work-1");
        order.setTenantCode("tenant-a");
        order.setWorkOrderNo("WO-1");
        order.setInternalProductNo("PART-1");
        order.setCustomerPartNo("CUSTOMER-PART-OTHER");
        return order;
    }

    private FlyingProbeRun run() {
        FlyingProbeRun run = new FlyingProbeRun();
        run.setTenantCode("tenant-a");
        run.setEquipmentId("equipment-1");
        run.setMaterialNo("PART-1");
        run.setWorkOrderNo("WO-1");
        run.setStatus("ACTIVE");
        run.setTestedCount(0);
        run.setGoodCount(0);
        run.setBadCount(0);
        return run;
    }

    private EquipmentTelemetryRecord event(
        String id, LocalDateTime start, LocalDateTime deviceTime, int tested, int good, int bad
    ) {
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId(id);
        record.setTenantCode("tenant-a");
        record.setEquipmentId("equipment-1");
        record.setMaterialNo("PART-1");
        record.setDeviceTime(deviceTime);
        record.setReceivedAt(LocalDateTime.now());
        record.setEventType("FLYING_PROBE_TEST_COUNTERS");
        record.setSourceRecordNo("SRC-" + id);
        record.setRawPayload("{\"attributes\":{\"sourceFile\":\"run.txt\",\"startTime\":\""
            + start + "\",\"testedBoardCount\":" + tested + ",\"goodBoardCount\":" + good
            + ",\"badBoardCount\":" + bad + "}}");
        return record;
    }

    private FlyingProbeProjection projection(EquipmentTelemetryRecord telemetry) {
        FlyingProbeProjection projection = new FlyingProbeProjection();
        projection.setTelemetryId(telemetry.getId());
        projection.setTenantCode(telemetry.getTenantCode());
        projection.setEquipmentId(telemetry.getEquipmentId());
        projection.setMaterialNo(telemetry.getMaterialNo());
        projection.setDeviceTime(telemetry.getDeviceTime());
        projection.setReceivedAt(telemetry.getReceivedAt());
        projection.setSourceRecordNo(telemetry.getSourceRecordNo());
        projection.setRawPayload(telemetry.getRawPayload());
        projection.setStatus("PENDING");
        return projection;
    }
}
