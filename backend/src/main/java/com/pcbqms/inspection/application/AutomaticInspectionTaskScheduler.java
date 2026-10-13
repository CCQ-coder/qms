package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Generates a task only when a matching active rule has no unfinished task for the same batch. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "qms.inspection.auto-generation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AutomaticInspectionTaskScheduler {
    private static final String ACTIVE_TENANTS_SQL = "SELECT code FROM qms_tenant WHERE status = 'ACTIVE' ORDER BY code";
    private final WorkOrderMapper workOrderMapper;
    private final InspectionTaskGenerationService inspectionTaskGenerationService;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelayString = "${qms.inspection.auto-generation.fixed-delay-ms:300000}")
    public void generatePendingTasks() {
        List<String> tenantCodes = jdbcTemplate.queryForList(ACTIVE_TENANTS_SQL, String.class);
        for (String tenantCode : tenantCodes) {
            TenantContext.set(tenantCode, "system.auto-inspection");
            try {
                List<WorkOrder> activeWorkOrders = workOrderMapper.selectList(Wrappers.lambdaQuery(WorkOrder.class)
                    .eq(WorkOrder::getTenantCode, tenantCode)
                    .eq(WorkOrder::getStatus, "ACTIVE"));
                for (WorkOrder workOrder : activeWorkOrders) {
                    try {
                        inspectionTaskGenerationService.generateForWorkOrder(workOrder.getWorkOrderNo(), null);
                    } catch (RuntimeException exception) {
                        log.warn("自动生成巡检任务失败，租户={}，工单={}", tenantCode, workOrder.getWorkOrderNo(), exception);
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }
    }
}
