package com.pcbqms.dashboard.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.dashboard.api.DashboardSummaryResponse;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.Nonconformity;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.NonconformityMapper;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardApplicationService {
    private final WorkOrderMapper workOrderMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final InspectionRecordMapper inspectionRecordMapper;
    private final NonconformityMapper nonconformityMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public DashboardSummaryResponse summary() {
        String tenantCode = TenantContext.tenantCode();
        var access = inspectionAccessScopeService.currentAccess();
        List<InspectionTask> tasks = inspectionTaskMapper.selectList(Wrappers.lambdaQuery(InspectionTask.class)
                .eq(InspectionTask::getTenantCode, tenantCode))
            .stream().filter(task -> access.fullDataAccess() || access.canAccess(task.getProcessCode())).toList();
        Set<String> taskIds = tasks.stream().map(InspectionTask::getId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> workOrderIds = tasks.stream().map(InspectionTask::getWorkOrderId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        long activeWorkOrders = access.fullDataAccess()
            ? workOrderMapper.selectCount(Wrappers.lambdaQuery(WorkOrder.class)
                .eq(WorkOrder::getTenantCode, tenantCode).eq(WorkOrder::getStatus, "ACTIVE"))
            : countActiveScopedWorkOrders(tenantCode, workOrderIds);
        long pendingTasks = tasks.stream().filter(task -> "PENDING".equals(task.getStatus())).count();
        long completedTasks = tasks.stream().filter(task -> "COMPLETED".equals(task.getStatus())).count();
        List<InspectionRecord> records = taskIds.isEmpty() ? List.of() : inspectionRecordMapper.selectList(Wrappers.lambdaQuery(InspectionRecord.class)
                .eq(InspectionRecord::getTenantCode, tenantCode).in(InspectionRecord::getTaskId, taskIds))
            .stream().filter(record -> taskIds.contains(record.getTaskId())).toList();
        long openNcrs = countOpenNonconformities(tenantCode, access.fullDataAccess(), taskIds);
        long totalRecords = records.size();
        long passRecords = records.stream().filter(record -> "PASS".equals(record.getVerdict())).count();
        double firstPassRate = totalRecords == 0 ? 0D : Math.round(passRecords * 10000D / totalRecords) / 100D;
        return new DashboardSummaryResponse(activeWorkOrders, pendingTasks, completedTasks, openNcrs, firstPassRate);
    }

    private long countActiveScopedWorkOrders(String tenantCode, Set<String> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return 0;
        }
        return workOrderMapper.selectCount(Wrappers.lambdaQuery(WorkOrder.class)
            .eq(WorkOrder::getTenantCode, tenantCode)
            .eq(WorkOrder::getStatus, "ACTIVE")
            .in(WorkOrder::getId, workOrderIds));
    }

    private long countOpenNonconformities(String tenantCode, boolean fullDataAccess, Set<String> visibleTaskIds) {
        if (fullDataAccess) {
            return nonconformityMapper.selectCount(Wrappers.lambdaQuery(Nonconformity.class)
                .eq(Nonconformity::getTenantCode, tenantCode).eq(Nonconformity::getStatus, "OPEN"));
        }
        if (visibleTaskIds.isEmpty()) {
            return 0;
        }
        return nonconformityMapper.selectList(Wrappers.lambdaQuery(Nonconformity.class)
                .eq(Nonconformity::getTenantCode, tenantCode).eq(Nonconformity::getStatus, "OPEN"))
            .stream()
            .map(ncr -> inspectionRecordMapper.selectById(ncr.getInspectionRecordId()))
            .filter(record -> record != null && tenantCode.equals(record.getTenantCode()))
            .filter(record -> visibleTaskIds.contains(record.getTaskId()))
            .count();
    }
}
