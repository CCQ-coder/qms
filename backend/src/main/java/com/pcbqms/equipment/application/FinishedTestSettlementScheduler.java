package com.pcbqms.equipment.application;

import com.pcbqms.common.tenancy.TenantContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinishedTestSettlementScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final FinishedTestDeviceReportService reportService;

    @Scheduled(fixedDelayString = "${qms.inspection.finished-test-settlement.fixed-delay-ms:60000}")
    public void settle() {
        for (String tenant : jdbcTemplate.queryForList(
            "SELECT code FROM qms_tenant WHERE status = 'ACTIVE' ORDER BY code", String.class)) {
            TenantContext.set(tenant, "system.finished-test-settlement");
            try {
                reportService.settleIdleMaterials(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            } catch (RuntimeException exception) {
                log.warn("成品检测静默结算失败，租户={}", tenant, exception);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
