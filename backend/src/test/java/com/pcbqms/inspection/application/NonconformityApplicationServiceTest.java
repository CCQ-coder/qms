package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.api.CloseCapaRequest;
import com.pcbqms.inspection.api.CreateCapaRequest;
import com.pcbqms.inspection.api.CreateNonconformityRequest;
import com.pcbqms.inspection.api.CreateManualNonconformityRequest;
import com.pcbqms.inspection.api.CreateNonconformityDetailRequest;
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
import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NonconformityApplicationServiceTest {
    @Mock private NonconformityMapper nonconformityMapper;
    @Mock private NonconformityDetailMapper nonconformityDetailMapper;
    @Mock private CapaMapper capaMapper;
    @Mock private InspectionRecordMapper inspectionRecordMapper;
    @Mock private InspectionTaskMapper inspectionTaskMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private ProductionBatchMapper productionBatchMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void inspectorCanManuallyCreateNcrForOwnFailedRecord() {
        TenantContext.set("customer-a", "tester.a");
        InspectionRecord record = new InspectionRecord();
        record.setId("record-1");
        record.setTenantCode("customer-a");
        record.setTaskId("task-1");
        record.setVerdict("FAIL");
        InspectionTask task = new InspectionTask();
        task.setId("task-1");
        task.setTenantCode("customer-a");
        task.setProcessCode("FQC");
        task.setWorkOrderId("work-order-1");
        task.setBatchId("batch-1");
        WorkOrder workOrder = new WorkOrder();
        workOrder.setWorkOrderNo("WO-001");
        ProductionBatch batch = new ProductionBatch();
        batch.setBatchNo("BATCH-001");
        when(inspectionRecordMapper.selectById("record-1")).thenReturn(record);
        when(inspectionTaskMapper.selectById("task-1")).thenReturn(task);
        when(nonconformityMapper.selectOne(any())).thenReturn(null);
        when(nonconformityMapper.selectCount(any())).thenReturn(0L);
        when(workOrderMapper.selectById("work-order-1")).thenReturn(workOrder);
        when(productionBatchMapper.selectById("batch-1")).thenReturn(batch);
        NonconformityApplicationService service = new NonconformityApplicationService(
            nonconformityMapper, capaMapper, inspectionRecordMapper, inspectionTaskMapper, inspectionAccessScopeService,
            workOrderMapper, productionBatchMapper, nonconformityDetailMapper);

        var response = service.createNonconformity(new CreateNonconformityRequest("record-1"));

        assertThat(response.ncrNo()).matches("NCR-\\d{8}-0001");
        assertThat(response.workOrderNo()).isEqualTo("WO-001");
        assertThat(response.batchNo()).isEqualTo("BATCH-001");
        assertThat(response.status()).isEqualTo("OPEN");
        ArgumentCaptor<Nonconformity> ncrCaptor = ArgumentCaptor.forClass(Nonconformity.class);
        verify(nonconformityMapper).insert(ncrCaptor.capture());
        assertThat(ncrCaptor.getValue().getInspectionRecordId()).isEqualTo("record-1");
        assertThat(ncrCaptor.getValue().getInitiatedBy()).isEqualTo("tester.a");
        verify(inspectionAccessScopeService).requireProcessAccess("FQC");
    }

    @Test
    void inspectorOnlySeesNonconformitiesFromAssignedProcesses() {
        TenantContext.set("customer-a", "tester.a");
        Nonconformity visible = nonconformity("ncr-1", "record-fqc");
        Nonconformity hidden = nonconformity("ncr-2", "record-mask");
        InspectionRecord fqcRecord = record("record-fqc", "task-fqc");
        InspectionRecord maskRecord = record("record-mask", "task-mask");
        InspectionTask fqcTask = task("task-fqc", "FQC");
        InspectionTask maskTask = task("task-mask", "SOLDER_MASK");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "INSPECTOR", false, Set.of("FQC")));
        when(nonconformityMapper.selectList(any())).thenReturn(List.of(visible, hidden));
        when(inspectionRecordMapper.selectById("record-fqc")).thenReturn(fqcRecord);
        when(inspectionRecordMapper.selectById("record-mask")).thenReturn(maskRecord);
        when(inspectionTaskMapper.selectById("task-fqc")).thenReturn(fqcTask);
        when(inspectionTaskMapper.selectById("task-mask")).thenReturn(maskTask);
        NonconformityApplicationService service = service();

        var result = service.listNonconformities(null);

        assertThat(result).extracting(item -> item.id()).containsExactly("ncr-1");
    }

    @Test
    void onlySupervisorOrAdministratorCanCloseCapa() {
        TenantContext.set("customer-a", "tester.a");
        doThrow(new org.springframework.security.access.AccessDeniedException("not supervisor"))
            .when(inspectionAccessScopeService).requireSupervisorOrAdministrator();
        NonconformityApplicationService service = service();

        assertThatThrownBy(() -> service.closeCapa("capa-1", new CloseCapaRequest("验证通过", null)))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verifyNoInteractions(capaMapper);
    }

    @Test
    void createsManualNonconformityWithCalculatedScrapArea() {
        TenantContext.set("customer-a", "tester.a");
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId("work-order-1");
        workOrder.setTenantCode("customer-a");
        workOrder.setWorkOrderNo("WO-001");
        workOrder.setInternalProductNo("INNER-001");
        workOrder.setSinglePcsArea(new BigDecimal("0.25"));
        when(workOrderMapper.selectOne(any())).thenReturn(workOrder);
        when(nonconformityMapper.selectCount(any())).thenReturn(0L);
        var request = new CreateManualNonconformityRequest(LocalDate.of(2026, 10, 20), "白班", "FQC", "成品测试",
            "品质部", "手工登记", "SCRAP", "BATCH-001", List.of(
                new CreateNonconformityDetailRequest("WO-001", "PCS", 4, "板边破损", "复核后报废")));

        var response = service().createManualNonconformity(request);

        assertThat(response.ncrNo()).matches("NCR-\\d{8}-0001");
        ArgumentCaptor<Nonconformity> ncr = ArgumentCaptor.forClass(Nonconformity.class);
        verify(nonconformityMapper).insert(ncr.capture());
        assertThat(ncr.getValue().getInspectionRecordId()).isNull();
        assertThat(ncr.getValue().getDispositionMethod()).isEqualTo("SCRAP");
        assertThat(response.details()).hasSize(1);
        assertThat(response.details().getFirst().scrapArea()).isEqualByComparingTo("1.00");
        verify(inspectionAccessScopeService).requireProcessAccess("FQC");
        verify(nonconformityDetailMapper).insert(any(NonconformityDetail.class));
    }

    @Test
    void manualNonconformityCanStartCapaUsingItsProcessAccess() {
        TenantContext.set("customer-a", "tester.a");
        Nonconformity ncr = nonconformity("ncr-1", null);
        ncr.setProcessCode("FQC");
        when(nonconformityMapper.selectById("ncr-1")).thenReturn(ncr);
        when(capaMapper.selectOne(any())).thenReturn(null);
        when(capaMapper.selectCount(any())).thenReturn(0L);

        service().createCapa("ncr-1", new CreateCapaRequest("板边破损", null, null, null, null, null));

        verify(inspectionAccessScopeService).requireProcessAccess("FQC");
        verifyNoInteractions(inspectionRecordMapper);
    }

    private NonconformityApplicationService service() {
        return new NonconformityApplicationService(nonconformityMapper, capaMapper, inspectionRecordMapper,
            inspectionTaskMapper, inspectionAccessScopeService, workOrderMapper, productionBatchMapper, nonconformityDetailMapper);
    }

    private Nonconformity nonconformity(String id, String recordId) {
        Nonconformity ncr = new Nonconformity();
        ncr.setId(id);
        ncr.setTenantCode("customer-a");
        ncr.setNcrNo("NCR-" + id);
        ncr.setInspectionRecordId(recordId);
        return ncr;
    }

    private InspectionRecord record(String id, String taskId) {
        InspectionRecord record = new InspectionRecord();
        record.setId(id);
        record.setTenantCode("customer-a");
        record.setTaskId(taskId);
        return record;
    }

    private InspectionTask task(String id, String processCode) {
        InspectionTask task = new InspectionTask();
        task.setId(id);
        task.setTenantCode("customer-a");
        task.setProcessCode(processCode);
        return task;
    }
}
