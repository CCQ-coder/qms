package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.inspection.api.CreateInspectionRecordRequest;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.Nonconformity;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTemplateMapper;
import com.pcbqms.inspection.infrastructure.persistence.NonconformityMapper;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import com.pcbqms.integration.application.InspectionAttachmentApplicationService;
import com.pcbqms.workorder.application.WorkOrderApplicationService;
import com.pcbqms.workorder.domain.ProductionBatch;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InspectionApplicationServiceTest {
    @Mock private InspectionTaskMapper inspectionTaskMapper;
    @Mock private InspectionRecordMapper inspectionRecordMapper;
    @Mock private NonconformityMapper nonconformityMapper;
    @Mock private SystemParameterMapper systemParameterMapper;
    @Mock private InspectionTemplateMapper inspectionTemplateMapper;
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private ProductionBatchMapper productionBatchMapper;
    @Mock private WorkOrderApplicationService workOrderApplicationService;
    @Mock private InspectionTemplateApplicationService inspectionTemplateApplicationService;
    @Mock private InspectionAttachmentApplicationService inspectionAttachmentApplicationService;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;
    @Mock private EquipmentMapper equipmentMapper;
    @Mock private ObjectMapper objectMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void repeatedClientRequestReturnsExistingRecordWithoutSubmittingAgain() {
        TenantContext.set("customer-a", "tester.a");
        InspectionTask task = new InspectionTask();
        task.setId("task-1");
        task.setTenantCode("customer-a");
        task.setProcessCode("FQC");
        task.setStatus("COMPLETED");
        InspectionRecord record = new InspectionRecord();
        record.setId("record-1");
        record.setTenantCode("customer-a");
        record.setTaskId("task-1");
        record.setVerdict("FAIL");
        record.setClientRequestId("client-request-1");
        Nonconformity ncr = new Nonconformity();
        ncr.setNcrNo("NCR-20260903-0001");
        when(inspectionTaskMapper.selectById("task-1")).thenReturn(task);
        when(inspectionRecordMapper.selectOne(any())).thenReturn(record);
        when(nonconformityMapper.selectOne(any())).thenReturn(ncr);
        InspectionApplicationService service = service();

        var result = service.submitRecord("task-1", new CreateInspectionRecordRequest(
            "FAIL", 1, "孔破", "J12", Map.of(), java.util.List.of(), "FQC-01", "client-request-1"));

        assertThat(result.id()).isEqualTo("record-1");
        assertThat(result.ncrNo()).isEqualTo("NCR-20260903-0001");
        verify(inspectionAccessScopeService).requireProcessAccess("FQC");
        verify(inspectionRecordMapper, never()).insert(any(InspectionRecord.class));
        verify(inspectionTaskMapper, never()).updateById(any(InspectionTask.class));
    }

    @Test
    void listsSubmittedFinishedRecordsWithDeviceAndProductionContext() {
        TenantContext.set("customer-a", "tester.a");
        InspectionTask task = new InspectionTask();
        task.setId("task-1");
        task.setTenantCode("customer-a");
        task.setTaskNo("IT-260909-0001");
        task.setWorkOrderId("work-order-1");
        task.setBatchId("batch-1");
        task.setProcessCode("FQC");
        task.setTaskType("FINISHED");
        task.setStatus("COMPLETED");
        InspectionRecord record = new InspectionRecord();
        record.setId("record-1");
        record.setTenantCode("customer-a");
        record.setTaskId("task-1");
        record.setEquipmentAssetNo("FQC-01");
        record.setVerdict("FAIL");
        record.setDefectiveQuantity(3);
        record.setDefectSummary("开路");
        record.setInspectedBy("tester.a");
        record.setInspectedAt(LocalDateTime.of(2026, 9, 9, 14, 30));
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId("work-order-1");
        workOrder.setWorkOrderNo("WO-260909-001");
        workOrder.setInternalProductNo("02PT41351A01");
        workOrder.setProductCode("PCB-4L-001");
        ProductionBatch batch = new ProductionBatch();
        batch.setId("batch-1");
        batch.setBatchNo("BATCH-260909-A01");
        batch.setPlannedQuantity(120);
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "INSPECTOR", false, Set.of("FQC")));
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(inspectionRecordMapper.selectFinishedPage(any(), any(), any(), any(), any(int.class)))
            .thenReturn(List.of(record));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of(batch));

        var results = service().listCompletedFinishedRecords(100, null).items();

        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.equipmentAssetNo()).isEqualTo("FQC-01");
            assertThat(item.workOrderNo()).isEqualTo("WO-260909-001");
            assertThat(item.batchNo()).isEqualTo("BATCH-260909-A01");
            assertThat(item.inspectionQuantity()).isEqualTo(120);
            assertThat(item.goodQuantity()).isEqualTo(117);
            assertThat(item.defectiveQuantity()).isEqualTo(3);
        });
    }

    @Test
    void finishedRecordUsesActualInspectedQuantityWhenSubmittedWithIncrementalCount() throws Exception {
        TenantContext.set("customer-a", "admin");
        InspectionTask task = new InspectionTask();
        task.setId("task-increment");
        task.setTenantCode("customer-a");
        task.setTaskNo("IT-FP-001");
        task.setWorkOrderId("work-order-1");
        task.setProcessCode("FQC");
        task.setTaskType("FINISHED");
        task.setStatus("COMPLETED");
        InspectionRecord record = new InspectionRecord();
        record.setId("record-increment");
        record.setTenantCode("customer-a");
        record.setTaskId("task-increment");
        record.setVerdict("FAIL");
        record.setDefectiveQuantity(1);
        record.setResultsJson("{\"inspectionQuantity\":2,\"goodBoardCount\":1,\"badBoardCount\":1}");
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId("work-order-1");
        workOrder.setPlannedQuantity(120);
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "TENANT_ADMIN", true, Set.of()));
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(inspectionRecordMapper.selectFinishedPage(any(), any(), any(), any(), any(int.class)))
            .thenReturn(List.of(record));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder));
        when(objectMapper.readValue(any(String.class), any(com.fasterxml.jackson.core.type.TypeReference.class)))
            .thenReturn(Map.of("inspectionQuantity", 2, "goodBoardCount", 1, "badBoardCount", 1));

        var results = service().listCompletedFinishedRecords(100, null).items();

        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.inspectionQuantity()).isEqualTo(2);
            assertThat(item.goodQuantity()).isEqualTo(1);
            assertThat(item.defectiveQuantity()).isEqualTo(1);
        });
    }

    @Test
    void automaticRecordUsesDeviceInspectionTimeAndAdminWhileKeepingCreatedAtNow() {
        TenantContext.set("customer-a", "admin");
        InspectionTask task = new InspectionTask();
        task.setId("task-auto");
        task.setTenantCode("customer-a");
        task.setProcessCode("FQC");
        task.setTaskType("FINISHED");
        task.setStatus("PENDING");
        when(inspectionTaskMapper.selectById("task-auto")).thenReturn(task);
        LocalDateTime deviceTime = LocalDateTime.of(2026, 10, 20, 15, 2);

        service().submitAutomaticRecord("task-auto", new CreateInspectionRecordRequest(
            "PASS", 0, null, null, Map.of("inspectionQuantity", 2), List.of(), null, "FP-source-1"), deviceTime);

        ArgumentCaptor<InspectionRecord> saved = ArgumentCaptor.forClass(InspectionRecord.class);
        verify(inspectionRecordMapper).insert(saved.capture());
        assertThat(saved.getValue().getInspectedAt()).isEqualTo(deviceTime);
        assertThat(saved.getValue().getCreatedAt()).isAfter(deviceTime);
        assertThat(saved.getValue().getInspectedBy()).isEqualTo("admin");
        verify(inspectionTaskMapper).updateById(task);
        assertThat(task.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void finishedRecordsUseStableCursorAndOnlyFetchOnePageAtATime() {
        TenantContext.set("customer-a", "admin");
        InspectionTask task = new InspectionTask();
        task.setId("task-page");
        task.setTenantCode("customer-a");
        task.setWorkOrderId("work-page");
        task.setProcessCode("FQC");
        task.setTaskType("FINISHED");
        task.setStatus("COMPLETED");
        WorkOrder order = new WorkOrder();
        order.setId("work-page");
        order.setWorkOrderNo("WO-PAGE");
        InspectionRecord newer = new InspectionRecord();
        newer.setId("record-b");
        newer.setTenantCode("customer-a");
        newer.setTaskId("task-page");
        newer.setInspectedAt(LocalDateTime.of(2026, 10, 22, 10, 1));
        InspectionRecord older = new InspectionRecord();
        older.setId("record-a");
        older.setTenantCode("customer-a");
        older.setTaskId("task-page");
        older.setInspectedAt(LocalDateTime.of(2026, 10, 22, 10, 0));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "TENANT_ADMIN", true, Set.of()));
        when(inspectionRecordMapper.selectFinishedPage(eq("customer-a"), isNull(), isNull(), isNull(), eq(2)))
            .thenReturn(List.of(newer, older));
        when(inspectionRecordMapper.selectFinishedPage(eq("customer-a"), isNull(), eq(newer.getInspectedAt()),
            eq(newer.getId()), eq(2))).thenReturn(List.of(older));
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(order));

        var first = service().listCompletedFinishedRecords(1, null);
        var second = service().listCompletedFinishedRecords(1, first.nextCursor());

        assertThat(first.items()).extracting(item -> item.id()).containsExactly("record-b");
        assertThat(first.nextCursor()).isNotBlank();
        assertThat(second.items()).extracting(item -> item.id()).containsExactly("record-a");
        assertThat(second.nextCursor()).isNull();
    }

    private InspectionApplicationService service() {
        return new InspectionApplicationService(inspectionTaskMapper, inspectionRecordMapper, nonconformityMapper,
            systemParameterMapper, inspectionTemplateMapper, workOrderMapper, productionBatchMapper, workOrderApplicationService,
            inspectionTemplateApplicationService, inspectionAttachmentApplicationService, inspectionAccessScopeService,
            equipmentMapper, objectMapper);
    }
}
