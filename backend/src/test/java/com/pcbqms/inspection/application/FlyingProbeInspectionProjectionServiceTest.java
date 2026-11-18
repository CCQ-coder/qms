package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.domain.FinishedTestDataWindow;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.inspection.api.CreateInspectionRecordRequest;
import com.pcbqms.inspection.api.FlyingProbeRetryableRecordResponse;
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
    @Mock private StandardBoardService standardBoardService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void standardBoardIsNotMatchedOrCounted() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("standard", now.minusHours(1), now.minusMinutes(1), 3, 3, 0);
        telemetry.setMaterialNo(" fzv1 ");
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("standard")).thenReturn(projection);
        when(standardBoardService.contains(" fzv1 ")).thenReturn(true);

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("SKIPPED");
        verify(workOrderMapper, never()).selectList(any());
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void standardBoardCannotBeBoundManually() {
        TenantContext.set("tenant-a", "supervisor");
        var now = LocalDateTime.now();
        var telemetry = event("standard-manual", now.minusHours(1), now.minusMinutes(1), 3, 3, 0);
        telemetry.setMaterialNo("FZV1");
        var projection = projection(telemetry);
        projection.setStatus("RETRYABLE");
        when(projectionMapper.lockProjection("standard-manual")).thenReturn(projection);
        when(standardBoardService.contains("FZV1")).thenReturn(true);

        assertThatThrownBy(() -> service().bindManually("standard-manual", "WO-1"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("标准板");
        verify(workOrderMapper, never()).selectOne(any());
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
    void manualBindingClaimsAnUnprojectedTelemetryRecord() {
        TenantContext.set("tenant-a", "supervisor");
        var now = LocalDateTime.now();
        var telemetry = event("unprojected", now.minusHours(1), now.minusMinutes(1), 3, 2, 1);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("unprojected")).thenReturn(null, projection);
        when(projectionMapper.selectTelemetryForClaim("tenant-a", "unprojected", FinishedTestDataWindow.START))
            .thenReturn(telemetry);
        when(workOrderMapper.selectOne(any())).thenReturn(workOrder());
        FlyingProbeRun run = run();
        when(projectionMapper.lockRun(any())).thenReturn(run);

        service().bindManually("unprojected", "WO-1");

        verify(projectionMapper).claim(telemetry);
        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
    }

    @Test
    void manualBindingRetriesAnUnboundSkippedProjection() {
        TenantContext.set("tenant-a", "supervisor");
        var now = LocalDateTime.now();
        var projection = projection(event("skipped", now.minusHours(1), now.minusMinutes(1), 3, 2, 1));
        projection.setStatus("SKIPPED");
        when(projectionMapper.lockProjection("skipped")).thenReturn(projection);
        when(workOrderMapper.selectOne(any())).thenReturn(workOrder());
        when(projectionMapper.lockRun(any())).thenReturn(run());

        service().bindManually("skipped", "WO-1");

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
    }

    @Test
    void manualBindingCorrectsExistingFileAndItsHistoricalFqc() {
        TenantContext.set("tenant-a", "supervisor");
        var telemetry = event("already-bound", LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        projection.setStatus("RETRYABLE");
        when(projectionMapper.lockProjection("already-bound")).thenReturn(projection);
        when(workOrderMapper.selectOne(any())).thenReturn(releasedOrder("WO-OTHER", "PART-1", LocalDate.now()));
        when(projectionMapper.lockRun(any())).thenReturn(run());

        service().bindManually("already-bound", "WO-OTHER");

        verify(projectionMapper).rebindRunTasks(eq("tenant-a"), any(), eq("WO-OTHER"));
        verify(projectionMapper).rebindRunTelemetry(eq("tenant-a"), any(), eq("WO-OTHER"));
        verify(projectionMapper).rebindRunProjections(eq("tenant-a"), any(), eq("WO-OTHER"));
        verify(projectionMapper).updateProjection(projection);
        verify(projectionMapper, never()).insertRunIfAbsent(any());
    }

    @Test
    void processedProjectionCanBeCorrectedWithoutCreatingNewFqc() {
        TenantContext.set("tenant-a", "supervisor");
        var telemetry = event("processed", LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        projection.setStatus("PROCESSED");
        projection.setRunKey("run-key");
        projection.setWorkOrderNo("WO-1");
        projection.setInspectionTaskId("task-1");
        projection.setInspectionRecordId("record-1");
        when(projectionMapper.lockProjection("processed")).thenReturn(projection);
        when(workOrderMapper.selectOne(any())).thenReturn(releasedOrder("WO-OTHER", "PART-1", LocalDate.now()));
        when(projectionMapper.lockRun("run-key")).thenReturn(run());

        service().bindManually("processed", "WO-OTHER");

        verify(projectionMapper).rebindRunTasks("tenant-a", "run-key", "WO-OTHER");
        verify(projectionMapper).rebindRunTelemetry("tenant-a", "run-key", "WO-OTHER");
        verify(projectionMapper).rebindRunProjections("tenant-a", "run-key", "WO-OTHER");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
    }

    @Test
    void correctionRejectsFilesWithDownstreamNonconformities() {
        TenantContext.set("tenant-a", "supervisor");
        var projection = projection(event("with-ncr", LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1), 3, 2, 1));
        projection.setStatus("PROCESSED");
        projection.setRunKey("run-key");
        projection.setWorkOrderNo("WO-1");
        when(projectionMapper.lockProjection("with-ncr")).thenReturn(projection);
        when(workOrderMapper.selectOne(any())).thenReturn(releasedOrder("WO-OTHER", "PART-1", LocalDate.now()));
        when(projectionMapper.lockRun("run-key")).thenReturn(run());
        when(projectionMapper.countRunNonconformities("tenant-a", "run-key")).thenReturn(1L);

        assertThatThrownBy(() -> service().bindManually("with-ncr", "WO-OTHER"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("处置");
        verify(projectionMapper, never()).rebindRunTasks(any(), any(), any());
    }

    @Test
    void correctionRejectsSettledFiles() {
        TenantContext.set("tenant-a", "supervisor");
        var projection = projection(event("settled", LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1), 3, 3, 0));
        projection.setStatus("PROCESSED");
        projection.setRunKey("run-key");
        projection.setWorkOrderNo("WO-1");
        when(projectionMapper.lockProjection("settled")).thenReturn(projection);
        when(workOrderMapper.selectOne(any())).thenReturn(releasedOrder("WO-OTHER", "PART-1", LocalDate.now()));
        when(projectionMapper.lockRun("run-key")).thenReturn(run());
        when(projectionMapper.countRunSettlements("tenant-a", "run-key", "WO-1")).thenReturn(1L);

        assertThatThrownBy(() -> service().bindManually("settled", "WO-OTHER"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("结算");
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
        when(projectionMapper.countRetryableGroups("tenant-a", FinishedTestDataWindow.START, null, null, List.of())).thenReturn(1L);
        when(projectionMapper.selectRetryableGroups("tenant-a", FinishedTestDataWindow.START, 50, 0L, null, null, List.of()))
            .thenReturn(List.of(record));

        var page = service().listRetryable(1, 50, null, null);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).extracting(FlyingProbeRetryableRecordResponse::getId)
            .containsExactly("old-telemetry");
    }

    @Test
    void searchesRetryableFilesAcrossAllPagesUsingBothFields() {
        TenantContext.set("tenant-a", "supervisor");
        when(projectionMapper.countRetryableGroups("tenant-a", FinishedTestDataWindow.START,
            "26100985", "P2L018680B", List.of())).thenReturn(1L);

        var page = service().listRetryable(1, 50, " 26100985 ", " P2L018680B ");

        assertThat(page.total()).isEqualTo(1);
        verify(projectionMapper).selectRetryableGroups("tenant-a", FinishedTestDataWindow.START,
            50, 0L, "26100985", "P2L018680B", List.of());
    }

    @Test
    void manualBindingRejectsAnotherTenantsProjection() {
        TenantContext.set("tenant-a", "supervisor");
        var telemetry = event("other-tenant", LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(1), 3, 3, 0);
        var projection = projection(telemetry);
        projection.setTenantCode("tenant-b");
        when(projectionMapper.lockProjection("other-tenant")).thenReturn(projection);

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
    void firstSnapshotAfterDataStartBaselinesAnOlderRun() {
        TenantContext.set("tenant-a", "admin");
        var telemetry = event("after-cutoff", LocalDateTime.of(2026, 10, 18, 9, 0),
            LocalDateTime.of(2026, 10, 20, 9, 0), 10, 8, 2);
        var projection = projection(telemetry);
        FlyingProbeRun run = run();
        run.setLastDeviceTime(LocalDateTime.of(2026, 10, 19, 23, 0));
        run.setTestedCount(7);
        run.setGoodCount(6);
        run.setBadCount(1);
        when(projectionMapper.lockProjection("after-cutoff")).thenReturn(projection);
        when(projectionMapper.lockRun(any())).thenReturn(run);
        when(workOrderMapper.selectOne(any())).thenReturn(workOrder());
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        validEquipmentAndTemplate();

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getDeltaTested()).isZero();
        assertThat(run.getTestedCount()).isEqualTo(10);
        verify(inspectionApplicationService, never()).submitAutomaticRecord(any(), any(), any());
        verify(projectionMapper).countEarlierUnresolved(projection, FinishedTestDataWindow.START);
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
        assertThat(projection.getReason()).contains("多个生产指令").doesNotContain("必须恰好为 1");
        verify(taskMapper, never()).insert(any(InspectionTask.class));
        verify(projectionMapper, never()).linkTelemetry(any());
    }

    @Test
    void hpVersionFallsBackToCombinedPanelAndProjectsUnderItsProductionOrder() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("hp-panel", now.minusHours(1), now.minusMinutes(1), 0, 0, 0);
        telemetry.setMaterialNo("hp009570a-1");
        var projection = projection(telemetry);
        var combinedOrder = releasedOrder("26101152", "P2L015440B", now.toLocalDate().minusDays(10));
        when(projectionMapper.lockProjection("hp-panel")).thenReturn(projection);
        validEquipmentAndTemplate();
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.findByCombinedPanelNo("tenant-a", "HP009570A")).thenReturn(List.of(combinedOrder));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
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

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("26101152");
        assertThat(savedRun.get().getBindingSource()).isEqualTo("AUTO_COMBINED");
        verify(workOrderMapper).findByCombinedPanelNo("tenant-a", "HP009570A");
    }

    @Test
    void boundCombinedPanelRunAcceptsItsNextSnapshot() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("hp-next", now.minusHours(1), now.minusMinutes(1), 0, 0, 0);
        telemetry.setMaterialNo("HP009570A-1");
        var projection = projection(telemetry);
        var combinedOrder = releasedOrder("26101152", "P2L015440B", now.toLocalDate().minusDays(10));
        FlyingProbeRun existing = run();
        existing.setMaterialNo("HP009570A-1");
        existing.setWorkOrderNo("26101152");
        existing.setBindingSource("AUTO_COMBINED");
        when(projectionMapper.lockProjection("hp-next")).thenReturn(projection);
        when(projectionMapper.lockRun(any())).thenReturn(existing);
        when(workOrderMapper.selectOne(any())).thenReturn(combinedOrder);
        when(workOrderMapper.findByCombinedPanelNo("tenant-a", "HP009570A")).thenReturn(List.of(combinedOrder));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        validEquipmentAndTemplate();

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("26101152");
    }

    @Test
    void versionedMaterialUsesLatestBaseOrderReleasedBeforeTheFileStarted() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var start = now.minusHours(1);
        var telemetry = event("versioned", start, now.minusMinutes(1), 0, 0, 0);
        telemetry.setMaterialNo("p2e046928a-1");
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("versioned")).thenReturn(projection);
        validEquipmentAndTemplate();
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(), List.of(
            releasedOrder("WO-OLD", "P2E046928A", start.toLocalDate().minusDays(10)),
            releasedOrder("WO-NEW", "P2E046928A", start.toLocalDate().minusDays(1)),
            releasedOrder("WO-FUTURE", "P2E046928A", start.toLocalDate().plusDays(1))));
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

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("WO-NEW");
        assertThat(savedRun.get().getBindingSource()).isEqualTo("AUTO_VERSION");
    }

    @Test
    void boundVersionedRunAcceptsItsNextSnapshot() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("versioned-next", now.minusHours(1), now.minusMinutes(1), 0, 0, 0);
        telemetry.setMaterialNo("P2E046928A-1");
        var projection = projection(telemetry);
        var order = releasedOrder("WO-NEW", "P2E046928A", now.toLocalDate().minusDays(1));
        FlyingProbeRun existing = run();
        existing.setMaterialNo("P2E046928A-1");
        existing.setWorkOrderNo("WO-NEW");
        existing.setBindingSource("AUTO_VERSION");
        when(projectionMapper.lockProjection("versioned-next")).thenReturn(projection);
        when(projectionMapper.lockRun(any())).thenReturn(existing);
        when(workOrderMapper.selectOne(any())).thenReturn(order);
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        validEquipmentAndTemplate();

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("WO-NEW");
    }

    @Test
    void minorVersionWithoutHyphenMatchesOnlyItsMajorVersionAndLatestOrder() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var start = now.minusDays(3);
        var telemetry = event("minor-a1", start, now.minusMinutes(1), 0, 0, 0);
        telemetry.setMaterialNo("p1l032679a1");
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("minor-a1")).thenReturn(projection);
        validEquipmentAndTemplate();
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(), List.of(
            releasedOrder("WO-A-OLD", "P1L032679A2", start.toLocalDate().minusDays(10)),
            releasedOrder("WO-A-NEW", "P1L032679A2", start.toLocalDate().minusDays(1)),
            releasedOrder("WO-B", "P1L032679B2", start.toLocalDate()),
            releasedOrder("WO-A-FUTURE", "P1L032679A2", start.toLocalDate().plusDays(1))));
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

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("WO-A-NEW");
        assertThat(savedRun.get().getBindingSource()).isEqualTo("AUTO_VERSION");
    }

    @Test
    void minorVersionWithHyphenCanMatchDifferentMinorVersionOfSameMajor() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("minor-a-hyphen", now.minusHours(1), now.minusMinutes(1), 0, 0, 0);
        telemetry.setMaterialNo("P1L032679A-1");
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("minor-a-hyphen")).thenReturn(projection);
        validEquipmentAndTemplate();
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(), List.of(), List.of(
            releasedOrder("WO-A2", "P1L032679A2", now.toLocalDate().minusDays(1))));
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

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("WO-A2");
    }

    @Test
    void boundMinorVersionRunAcceptsItsNextSnapshot() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var telemetry = event("minor-next", now.minusHours(1), now.minusMinutes(1), 0, 0, 0);
        telemetry.setMaterialNo("P1L032679A1");
        var projection = projection(telemetry);
        var order = releasedOrder("WO-A2", "P1L032679A2", now.toLocalDate().minusDays(1));
        FlyingProbeRun existing = run();
        existing.setMaterialNo("P1L032679A1");
        existing.setWorkOrderNo("WO-A2");
        existing.setBindingSource("AUTO_VERSION");
        when(projectionMapper.lockProjection("minor-next")).thenReturn(projection);
        when(projectionMapper.lockRun(any())).thenReturn(existing);
        when(workOrderMapper.selectOne(any())).thenReturn(order);
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        validEquipmentAndTemplate();

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
        assertThat(projection.getWorkOrderNo()).isEqualTo("WO-A2");
    }

    @Test
    void choosesLatestOrderReleasedBeforeTheFileStarted() {
        TenantContext.set("tenant-a", "admin");
        var now = LocalDateTime.now();
        var start = now.minusHours(1);
        var telemetry = event("latest-order", start, now.minusMinutes(1), 0, 0, 0);
        var projection = projection(telemetry);
        when(projectionMapper.lockProjection("latest-order")).thenReturn(projection);
        validEquipmentAndTemplate();
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(
            releasedOrder("WO-OLD", "PART-1", start.toLocalDate().minusDays(47)),
            releasedOrder("WO-NEW", "PART-1", start.toLocalDate().minusDays(30)),
            releasedOrder("WO-FUTURE", "PART-1", start.toLocalDate().plusDays(1))));
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

        service().projectNew(telemetry);

        assertThat(savedRun.get()).isNotNull();
        assertThat(savedRun.get().getWorkOrderNo()).isEqualTo("WO-NEW");
        assertThat(projection.getStatus()).isEqualTo("PROCESSED");
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
        WorkOrder firstOrder = workOrder();
        firstOrder.setReleasedAt(now.toLocalDate().minusDays(1));
        WorkOrder secondOrder = workOrder();
        secondOrder.setId("work-2");
        secondOrder.setWorkOrderNo("WO-2");
        secondOrder.setInternalProductNo(" part-1 ");
        secondOrder.setReleasedAt(firstOrder.getReleasedAt());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(firstOrder, secondOrder));

        service().projectNew(telemetry);

        assertThat(projection.getStatus()).isEqualTo("RETRYABLE");
        assertThat(projection.getReason()).contains("多个生产指令").doesNotContain("必须恰好为 1");
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
        assertThat(projection.getReason()).contains("未找到对应生产指令").doesNotContain("必须恰好为 1");
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
            templateApplicationService, accessScopeService, new ObjectMapper(), standardBoardService);
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
