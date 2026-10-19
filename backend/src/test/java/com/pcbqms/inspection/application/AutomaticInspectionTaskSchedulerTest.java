package com.pcbqms.inspection.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AutomaticInspectionTaskSchedulerTest {
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private InspectionTaskGenerationService inspectionTaskGenerationService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createsTasksWithinEachTenantContextAndClearsItAfterwards() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), WorkOrder.class);
        WorkOrder workOrder = new WorkOrder();
        workOrder.setWorkOrderNo("WO-100");
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("customer-a"));
        when(workOrderMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            org.junit.jupiter.api.Assertions.assertEquals("customer-a", TenantContext.tenantCode());
            Wrapper<?> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("tenant_code");
            return List.of(workOrder);
        });
        doAnswer(invocation -> {
            org.junit.jupiter.api.Assertions.assertEquals("customer-a", TenantContext.tenantCode());
            return null;
        }).when(inspectionTaskGenerationService).generateForWorkOrder("WO-100", null);

        new AutomaticInspectionTaskScheduler(workOrderMapper, inspectionTaskGenerationService, jdbcTemplate)
            .generatePendingTasks();

        verify(inspectionTaskGenerationService).generateForWorkOrder("WO-100", null);
        assertThrows(IllegalStateException.class, TenantContext::tenantCode);
    }
}
