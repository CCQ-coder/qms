package com.pcbqms.workorder.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.workorder.api.CreateWorkOrderRequest;
import com.pcbqms.workorder.api.WorkOrderQueryRequest;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.domain.ProductionBatch;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
    void pagedWorkOrdersSortByDisplayedMaterialInputDateBeforePagination() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), WorkOrder.class);
        TenantContext.set("customer-a", "tester.a");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "TENANT_ADMIN", true, Set.of()));
        when(workOrderMapper.selectCount(any())).thenReturn(1L);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(workOrder("order-1", "WO-1")));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of());

        service().page(new WorkOrderQueryRequest(null, null, null, null, null, null, null, null,
            null, null, null, null, 1L, 20L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<WorkOrder>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(workOrderMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains(
            "ORDER BY COALESCE(material_input_date, released_at) DESC, updated_at DESC, id DESC LIMIT 20 OFFSET 0");
    }

    @Test
    void pagedWorkOrdersLoadBatchesOnceForAllRows() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), WorkOrder.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ProductionBatch.class);
        TenantContext.set("customer-a", "tester.a");
        WorkOrder first = workOrder("order-1", "WO-1");
        WorkOrder second = workOrder("order-2", "WO-2");
        ProductionBatch firstBatch = new ProductionBatch();
        firstBatch.setWorkOrderId("order-1");
        firstBatch.setBatchNo("B-1");
        ProductionBatch secondBatch = new ProductionBatch();
        secondBatch.setWorkOrderId("order-2");
        secondBatch.setBatchNo("B-2");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "TENANT_ADMIN", true, Set.of()));
        when(workOrderMapper.selectCount(any())).thenReturn(2L);
        when(workOrderMapper.selectList(any())).thenReturn(List.of(first, second));
        when(productionBatchMapper.selectList(any())).thenReturn(List.of(firstBatch, secondBatch));

        var page = service().page(new WorkOrderQueryRequest(null, null, null, null, null, null, null, null,
            null, null, null, null, 1L, 20L));

        assertThat(page.records()).extracting(item -> item.batches().stream().map(batch -> batch.batchNo()).toList())
            .containsExactly(List.of("B-1"), List.of("B-2"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ProductionBatch>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(productionBatchMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("workOrderId IN");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains("customer-a", "order-1", "order-2");
    }

    @Test
    void warehouseStatusFilterMatchesErpTextAndCode() {
        Configuration configuration = new Configuration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), WorkOrder.class);
        TenantContext.set("customer-a", "tester.a");
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "TENANT_ADMIN", true, Set.of()));
        when(workOrderMapper.selectCount(any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("warehouseStatus");
            assertThat(query.getParamNameValuePairs().values()).contains("PARTIAL", "部分入库");
            return 0L;
        });

        service().page(new WorkOrderQueryRequest(null, null, null, null, null, null, "PARTIAL",
            null, null, null, null, null, 1L, 20L));
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
