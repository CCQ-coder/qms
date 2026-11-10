package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.pcbqms.inspection.api.FlyingProbeRetryableRecordResponse;
import com.pcbqms.inspection.api.InspectionRecordResponse;
import com.pcbqms.inspection.domain.FlyingProbeProjection;
import com.pcbqms.inspection.domain.FlyingProbeRun;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.InspectionTemplate;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.FlyingProbeProjectionMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTemplateMapper;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
    @Mock private SystemParameterMapper systemParameterMapper;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void manualBindingProjectsAFileWithNoNumericMaterialUnderTheChosenOrder() {
        TenantContext.set("tenant-a", "supervisor");
        var now = LocalDateTime.now();
        var telemetry = event("manual", now.minusHours(1), now.minusMinutes(1), 3, 2, 1);
        telemetry.setMaterialNo("NO-MATERIAL-NUMBER");
        var projection = projection(telemetry);
        projection.setStatus("RETRYABLE");
        projection.setRetryAfter(now.minusMinutes(1));
        when(projectionMapper.lockProjection("manual")).thenReturn(projection);
        WorkOrder selected = releasedOrder("WO-SELECTED", "OTHER-PRODUCT", now.toLocalDate());
        when(workOrderMapper.selectOne(any())).thenReturn(selected);
        AtomicReference<FlyingProbeRun> savedRun = new AtomicReference<>();
        when(projectionMapper.lockRun(any())).thenAnswer(call -> savedRun.get());
        when(projectionMapper.insertRunIfAbsent(any())).thenAnswer(call -> {
            FlyingProbeRun inserted = call.getArgument(0);
            inserted.setStatus("ACTIVE");
            inserted.setTestedCount(0);
            inserted.setGoodCount(0);
            inserted.setBadCount(0);
            savedRun.set(inserted);
            return 1;
        });
        validEquipmentAndTemplate();
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        when(inspectionApplicationService.submitAutomaticRecord(any(), any(), any())).thenReturn(
            new InspectionRecordResponse("record-manual", "task-manual", "FAIL", 1, null, null, null, now));

        var service = service();
        service.bindManually("manual", "WO-SELECTED");
        assertThat(savedRun.get().getWorkOrderNo()).isEqualTo("WO-SELECTED");
        assertThat(savedRun.get().getBindingSource()).isEqualTo("MANUAL");
        assertThat(savedRun.get().getBoundBy()).isEqualTo("supervisor");
        TenantContext.set("tenant-a", "admin");
        service.retry(projection);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("WO-SELECTED");
        assertThat(projection.getDeltaTested()).isEqualTo(3);
        verify(taskMapper).insert(any(InspectionTask.class));
    }

    @Test
    void manualBindingCannotChangeTheOrderOfAnExistingFile() {
        TenantContext.set("tenant-a", "supervisor");
        var telemetry = event("already-bound", LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        projection.setStatus("RETRYABLE");
        when(projectionMapper.lockProjection("already-bound")).thenReturn(projection);
        when(workOrderMapper.selectOne(any())).thenReturn(releasedOrder("WO-OTHER", "PART-1", LocalDate.now()));
        when(projectionMapper.lockRun(any())).thenReturn(run());

        assertThatThrownBy(() -> service().bindManually("already-bound", "WO-OTHER"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("已绑定");
        verify(projectionMapper, never()).insertRunIfAbsent(any());
        verify(projectionMapper, never()).wakeRetryableRun(any(), any());
    }

    @Test
    void processedFileCorrectionMovesTheWholeRunAndItsInspectionTasks() {
        TenantContext.set("tenant-a", "supervisor");
        var projection = projection(event("processed", LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1), 5, 4, 1));
        projection.setStatus("PROCESSED");
        projection.setRunKey("run-1");
        projection.setWorkOrderNo("WO-1");
        FlyingProbeRun run = run();
        run.setRunKey("run-1");
        when(projectionMapper.lockProjection("processed")).thenReturn(projection);
        when(projectionMapper.lockRun("run-1")).thenReturn(run);
        when(workOrderMapper.selectOne(any())).thenReturn(releasedOrder("WO-2", "PART-1", LocalDate.now()));

        assertThat(service().bindManually("processed", "WO-2")).isEqualTo("run-1");

        verify(projectionMapper).rebindRunTasks("tenant-a", "run-1", "WO-2");
        verify(projectionMapper).rebindRunTelemetry("tenant-a", "run-1", "WO-2");
        verify(projectionMapper).rebindRunRuntime("tenant-a", "run-1", "WO-2");
        verify(projectionMapper).rebindRunProjections("tenant-a", "run-1", "WO-2");
        assertThat(run.getWorkOrderNo()).isEqualTo("WO-2");
        assertThat(run.getBoundBy()).isEqualTo("supervisor");
        verify(projectionMapper).updateRun(run);
    }

    @Test
    void processedFileCorrectionCannotMoveReviewedDefects() {
        TenantContext.set("tenant-a", "supervisor");
        var projection = projection(event("reviewed", LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1), 5, 4, 1));
        projection.setStatus("PROCESSED");
        projection.setRunKey("run-1");
        FlyingProbeRun run = run();
        run.setRunKey("run-1");
        when(projectionMapper.lockProjection("reviewed")).thenReturn(projection);
        when(projectionMapper.lockRun("run-1")).thenReturn(run);
        when(workOrderMapper.selectOne(any())).thenReturn(releasedOrder("WO-2", "PART-1", LocalDate.now()));
        when(projectionMapper.countApprovedRunDates("tenant-a", "run-1", "WO-1")).thenReturn(1L);

        assertThatThrownBy(() -> service().bindManually("reviewed", "WO-2"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("处置单");
        verify(projectionMapper, never()).rebindRunTasks(any(), any(), any());
    }

    @Test
    void repeatingTheSameManualBindingKeepsTheOriginalAudit() {
        TenantContext.set("tenant-a", "second-supervisor");
        var now = LocalDateTime.now();
        var telemetry = event("repeat", now.minusHours(1), now.minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        projection.setStatus("RETRYABLE");
        when(projectionMapper.lockProjection("repeat")).thenReturn(projection);
        when(workOrderMapper.selectOne(any())).thenReturn(workOrder());
        FlyingProbeRun existing = run();
        existing.setBindingSource("MANUAL");
        existing.setBoundBy("first-supervisor");
        existing.setBoundAt(now.minusDays(1));
        when(projectionMapper.lockRun(any())).thenReturn(existing);

        service().bindManually("repeat", "WO-1");

        assertThat(existing.getBoundBy()).isEqualTo("first-supervisor");
        assertThat(existing.getBoundAt()).isEqualTo(now.minusDays(1));
    }

    @Test
    void listsPersistedRetryableFilesWithinTheCurrentTenant() {
        TenantContext.set("tenant-a", "supervisor");
        var record = new FlyingProbeRetryableRecordResponse();
        record.setId("old-telemetry");
        record.setSourceFile("WTD-9/old.txt");
        when(projectionMapper.countRetryableGroups("tenant-a", null, null, null, null, null)).thenReturn(1L);
        when(projectionMapper.selectRetryableGroups("tenant-a", 50, 0L, null, null, null, null, null)).thenReturn(List.of(record));

        var page = service().listRetryable(1, 50, null, null, null, null, null);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(FlyingProbeRetryableRecordResponse::getId)
            .containsExactly("old-telemetry");
    }

    @Test
    void retryableListFiltersBothCountAndRowsByDetectionTimeAndFactoryNumber() {
        TenantContext.set("tenant-a", "supervisor");
        LocalDateTime from = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 9, 23, 59, 59);
        when(projectionMapper.countRetryableGroups("tenant-a", null, null, "02PT42777", from, to)).thenReturn(1L);

        service().listRetryable(1, 50, null, null, " 02PT42777 ", from, to);

        verify(projectionMapper).countRetryableGroups("tenant-a", null, null, "02PT42777", from, to);
        verify(projectionMapper).selectRetryableGroups("tenant-a", 50, 0L, null, null, "02PT42777", from, to);
    }

    @Test
    void manualBindingRejectsAnotherTenantsProjectionAndCompletedRecords() {
        TenantContext.set("tenant-a", "supervisor");
        var telemetry = event("other-tenant", LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        projection.setTenantCode("tenant-b");
        when(projectionMapper.lockProjection("other-tenant")).thenReturn(projection);

        assertThatThrownBy(() -> service().bindManually("other-tenant", "WO-1"))
            .isInstanceOf(IllegalArgumentException.class);
        projection.setTenantCode("tenant-a");
        projection.setStatus("PROCESSED");
        assertThatThrownBy(() -> service().bindManually("other-tenant", "WO-1"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(projectionMapper, never()).insertRunIfAbsent(any());
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
    void configuredMiddleDigitsChooseLatestOrderReleasedBeforeFileStart() {
        TenantContext.set("tenant-a", "admin");
        var telemetry = event("dated", LocalDateTime.of(2026, 8, 26, 10, 7),
            LocalDateTime.now().minusMinutes(1), 12, 8, 4);
        telemetry.setMaterialNo("42777");
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("dated")).thenReturn(projection);
        validEquipmentAndTemplate();
        configuredMaterialRule();
        when(workOrderMapper.selectList(any())).thenReturn(List.of(
            releasedOrder("26040931", "02PT42777A01", LocalDate.of(2026, 4, 24)),
            releasedOrder("26070785", "02PT42777A01", LocalDate.of(2026, 7, 29)),
            releasedOrder("26080450", "02PT42777A01", LocalDate.of(2026, 8, 14)),
            releasedOrder("26100529", "02PT42777A01", LocalDate.of(2026, 10, 21))));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        FlyingProbeRun run = run();
        run.setMaterialNo("42777");
        run.setWorkOrderNo("26080450");
        when(projectionMapper.lockRun(any())).thenReturn(null, run);

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("26080450");
        assertThat(projection.getReason()).contains("仅建立基线");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void configuredMiddleDigitsKeepSameDayOrderTieRetryable() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("same-day", now.minusHours(1), now.minusMinutes(1), 2, 2, 0);
        telemetry.setMaterialNo("08169");
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("same-day")).thenReturn(projection);
        validEquipment();
        configuredMaterialRule();
        LocalDate releasedAt = now.toLocalDate().minusDays(1);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(
            releasedOrder("WO-1", "02PT08169A01", releasedAt),
            releasedOrder("WO-2", "02PT08169A01", releasedAt)));

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
        assertThat(projection.getReason()).contains("下达日期", "数量为 2");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void configuredRuleDoesNotChooseAroundAnOrderMissingReleaseDate() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("undated-order", now.minusHours(1), now.minusMinutes(1), 2, 2, 0);
        telemetry.setMaterialNo("42777");
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("undated-order")).thenReturn(projection);
        validEquipment();
        configuredMaterialRule();
        when(workOrderMapper.selectList(any())).thenReturn(List.of(
            releasedOrder("WO-DATED", "02PT42777A01", now.toLocalDate().minusDays(1)),
            releasedOrder("WO-UNDATED", "02PT42777A01", null)));

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
        assertThat(projection.getReason()).contains("下达日期缺失");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void configuredRuleRequiresNumericDeviceMaterial() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("nonnumeric", now.minusHours(1), now.minusMinutes(1), 2, 2, 0);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("nonnumeric")).thenReturn(projection);
        validEquipment();
        configuredMaterialRule();

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
        assertThat(projection.getReason()).contains("数字料号");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void configuredRuleKeepsAnAlreadyBoundFileOnItsOriginalOrder() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("bound-numeric", now.minusDays(20), now.minusMinutes(1), 12, 8, 4);
        telemetry.setMaterialNo("42777");
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("bound-numeric")).thenReturn(projection);
        validEquipmentAndTemplate();
        configuredMaterialRule();
        FlyingProbeRun run = run();
        run.setMaterialNo("42777");
        run.setWorkOrderNo("26080450");
        run.setLastDeviceTime(now.minusDays(19));
        run.setTestedCount(8);
        run.setGoodCount(5);
        run.setBadCount(3);
        when(projectionMapper.lockRun(any())).thenReturn(run);
        when(workOrderMapper.selectOne(any())).thenReturn(
            releasedOrder("26080450", "02PT42777A01", now.toLocalDate().minusDays(30)));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        when(inspectionApplicationService.submitAutomaticRecord(any(), any(), any())).thenReturn(
            new InspectionRecordResponse("record-numeric", "task-numeric", "FAIL", 1, null, null, null, now));

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("26080450");
        assertThat(projection.getDeltaTested()).isEqualTo(4);
        verify(taskMapper).insert(any(InspectionTask.class));
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
            templateApplicationService, accessScopeService, systemParameterMapper, new ObjectMapper());
    }

    private void configuredMaterialRule() {
        SystemParameter parameter = new SystemParameter();
        parameter.setParameterValue("^02PT([0-9]{5})A[0-9]{2}$");
        when(systemParameterMapper.selectOne(any())).thenReturn(parameter);
    }

    private WorkOrder releasedOrder(String orderNo, String internalProductNo, LocalDate releasedAt) {
        WorkOrder order = workOrder();
        order.setId(orderNo);
        order.setWorkOrderNo(orderNo);
        order.setInternalProductNo(internalProductNo);
        order.setReleasedAt(releasedAt);
        return order;
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
