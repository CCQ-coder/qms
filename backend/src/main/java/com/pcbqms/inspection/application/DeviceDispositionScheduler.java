package com.pcbqms.inspection.application;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.application.FinishedTestDeviceSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDispositionScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final FinishedTestDeviceSummaryService summaryService;
    private final NonconformityApplicationService nonconformityService;

    @Scheduled(fixedDelayString = "${qms.inspection.device-disposition.fixed-delay-ms:60000}")
    public void sync() {
        for (String tenant : jdbcTemplate.queryForList(
            "SELECT code FROM qms_tenant WHERE status = 'ACTIVE' ORDER BY code", String.class)) {
            TenantContext.set(tenant, "admin");
            try {
                summaryService.matchUnboundZhongbo();
                nonconformityService.syncDeviceDefects(summaryService.summariesForCurrentTenant());
            } catch (RuntimeException exception) {
                log.warn("设备不良品处置单汇总失败，租户={}", tenant, exception);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
