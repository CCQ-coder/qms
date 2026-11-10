package com.pcbqms.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.Nonconformity;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.NonconformityMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardApplicationServiceTest {
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private InspectionTaskMapper inspectionTaskMapper;
    @Mock private InspectionRecordMapper inspectionRecordMapper;
    @Mock private NonconformityMapper nonconformityMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void inspectorSummaryExcludesOtherProcessData() {
        TenantContext.set("customer-a", "tester.a");
        InspectionTask fqcTask = task("task-fqc", "work-order-fqc", "FQC", "PENDING");
        InspectionTask maskTask = task("task-mask", "work-order-mask", "SOLDER_MASK", "COMPLETED");
        InspectionRecord fqcRecord = record("record-fqc", "task-fqc", "PASS");
        InspectionRecord maskRecord = record("record-mask", "task-mask", "FAIL");
        Nonconformity maskNcr = new Nonconformity();
        maskNcr.setTenantCode("customer-a");
        maskNcr.setInspectionRecordId("record-mask");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "INSPECTOR", false, Set.of("FQC")));
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(fqcTask, maskTask));
        when(workOrderMapper.selectCount(any())).thenReturn(1L);
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(fqcRecord, maskRecord));
        when(nonconformityMapper.selectList(any())).thenReturn(List.of(maskNcr));
        when(inspectionRecordMapper.selectById("record-mask")).thenReturn(maskRecord);
        DashboardApplicationService service = new DashboardApplicationService(workOrderMapper, inspectionTaskMapper,
            inspectionRecordMapper, nonconformityMapper, inspectionAccessScopeService);

        var summary = service.summary();

        assertThat(summary.activeWorkOrders()).isEqualTo(1);
        assertThat(summary.pendingInspectionTasks()).isEqualTo(1);
        assertThat(summary.completedInspectionTasks()).isZero();
        assertThat(summary.openNonconformities()).isZero();
        assertThat(summary.firstPassRate()).isEqualTo(100D);
    }

    @Test
    void administratorSummaryCountsInDatabaseWithoutLoadingEveryRecord() {
        TenantContext.set("customer-a", "admin");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("admin", "TENANT_ADMIN", true, Set.of()));
        when(workOrderMapper.selectCount(any())).thenReturn(12L);
        when(inspectionTaskMapper.selectCount(any())).thenReturn(3L, 9L);
        when(inspectionRecordMapper.selectCount(any())).thenReturn(40L, 37L);
        when(nonconformityMapper.selectCount(any())).thenReturn(2L);

        var summary = new DashboardApplicationService(workOrderMapper, inspectionTaskMapper,
            inspectionRecordMapper, nonconformityMapper, inspectionAccessScopeService).summary();

        assertThat(summary.activeWorkOrders()).isEqualTo(12);
        assertThat(summary.pendingInspectionTasks()).isEqualTo(3);
        assertThat(summary.completedInspectionTasks()).isEqualTo(9);
        assertThat(summary.firstPassRate()).isEqualTo(92.5);
        verify(inspectionRecordMapper, never()).selectList(any());
        verify(inspectionTaskMapper, never()).selectList(any());
    }

    private InspectionTask task(String id, String workOrderId, String processCode, String status) {
        InspectionTask task = new InspectionTask();
        task.setId(id);
        task.setTenantCode("customer-a");
        task.setWorkOrderId(workOrderId);
        task.setProcessCode(processCode);
        task.setStatus(status);
        return task;
    }

    private InspectionRecord record(String id, String taskId, String verdict) {
        InspectionRecord record = new InspectionRecord();
        record.setId(id);
        record.setTenantCode("customer-a");
        record.setTaskId(taskId);
        record.setVerdict(verdict);
        return record;
    }
}
