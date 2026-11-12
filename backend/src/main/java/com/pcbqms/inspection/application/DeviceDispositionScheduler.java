package com.pcbqms.inspection.application;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.application.FinishedTestDeviceSummaryService;
import java.time.LocalDateTime;
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

    @Scheduled(fixedDelayString = "${qms.inspection.device-disposition.fixed-delay-ms:60000}")
    public void sync() {
        for (String tenant : jdbcTemplate.queryForList(
            "SELECT code FROM qms_tenant WHERE status = 'ACTIVE' ORDER BY code", String.class)) {
            TenantContext.set(tenant, "admin");
            try {
                try {
                    summaryService.matchUnboundZhongbo();
                } catch (RuntimeException exception) {
                    log.warn("众博信设备记录自动关联失败，租户={}", tenant, exception);
                }
                try {
                    summaryService.settleIdleMaterials(LocalDateTime.now());
                } catch (RuntimeException exception) {
                    log.warn("成品检测静默结算失败，租户={}", tenant, exception);
                }
            } finally {
                TenantContext.clear();
            }
        }
    }
}
