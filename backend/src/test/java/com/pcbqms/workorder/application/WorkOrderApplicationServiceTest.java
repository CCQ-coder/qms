package com.pcbqms.workorder.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.workorder.api.CreateWorkOrderRequest;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class WorkOrderApplicationServiceTest {
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private ProductionBatchMapper productionBatchMapper;
    @Mock private InspectionTaskMapper inspectionTaskMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void inspectorOnlyListsWorkOrdersWithAssignedProcessTasks() {
        TenantContext.set("customer-a", "tester.a");
        InspectionTask fqcTask = task("work-order-fqc", "FQC");
        InspectionTask maskTask = task("work-order-mask", "SOLDER_MASK");
        WorkOrder visible = workOrder("work-order-fqc", "WO-FQC");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "INSPECTOR", false, Set.of("FQC")));
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(fqcTask, maskTask));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(visible));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());
        WorkOrderApplicationService service = service();

        var result = service.list(null);

        assertThat(result).extracting(item -> item.workOrderNo()).containsExactly("WO-FQC");
    }

    @Test
    void manualWorkOrderMaintenanceRequiresSupervisorOrAdministrator() {
        TenantContext.set("customer-a", "tester.a");
        doThrow(new AccessDeniedException("not manager")).when(inspectionAccessScopeService).requireSupervisorOrAdministrator();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().upsert(new CreateWorkOrderRequest(
            "WO-1", "PCB-1", "板材", null, "FQC", "成品检验", 1, null, "MANUAL", null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderMapper, productionBatchMapper, inspectionTaskMapper);
    }

    @Test
    void snapshotOnlyDeactivatesMissingExcelGatewayWorkOrders() {
        TenantContext.set("customer-a", "gateway");
        WorkOrder retained = workOrder("retained", "WO-RETAINED");
        retained.setSourceKey("ERP-EXCEL:WO-RETAINED");
        retained.setSourceSystem("GATEWAY");
        retained.setStatus("ACTIVE");
        WorkOrder missing = workOrder("missing", "WO-MISSING");
        missing.setSourceKey("ERP-EXCEL:WO-MISSING");
        missing.setSourceSystem("GATEWAY");
        missing.setStatus("ACTIVE");
        when(workOrderMapper.selectList(any())).thenReturn(List.of(retained, missing));

        int deactivated = service().deactivateMissingGatewayWorkOrders(
            "ERP-EXCEL:", List.of("ERP-EXCEL:WO-RETAINED"));

        assertThat(deactivated).isEqualTo(1);
        assertThat(retained.getStatus()).isEqualTo("ACTIVE");
        assertThat(missing.getStatus()).isEqualTo("INACTIVE");
        verify(workOrderMapper).updateById(missing);
    }

    @Test
    void reactivatesAnExcelGatewayWorkOrderWhenItReappears() {
        TenantContext.set("customer-a", "gateway");
        WorkOrder workOrder = workOrder("order-1", "WO-1");
        workOrder.setStatus("INACTIVE");
        when(workOrderMapper.selectOne(any())).thenReturn(workOrder);
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());

        service().upsertFromIntegration(new CreateWorkOrderRequest(
            "WO-1", "PCB-1", "板材", null, "FQC", "成品检验", 1, null,
            "GATEWAY", "ERP-EXCEL:WO-1", "v1", null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertThat(workOrder.getStatus()).isEqualTo("ACTIVE");
        verify(workOrderMapper).updateById(workOrder);
    }

    private WorkOrderApplicationService service() {
        return new WorkOrderApplicationService(workOrderMapper, productionBatchMapper, inspectionTaskMapper, inspectionAccessScopeService);
    }

    private InspectionTask task(String workOrderId, String processCode) {
        InspectionTask task = new InspectionTask();
        task.setTenantCode("customer-a");
        task.setWorkOrderId(workOrderId);
        task.setProcessCode(processCode);
        return task;
    }

    private WorkOrder workOrder(String id, String workOrderNo) {
        WorkOrder workOrder = new WorkOrder();
        workOrder.setId(id);
        workOrder.setTenantCode("customer-a");
        workOrder.setWorkOrderNo(workOrderNo);
        return workOrder;
    }
}
