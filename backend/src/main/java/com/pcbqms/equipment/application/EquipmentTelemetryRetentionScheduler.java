package com.pcbqms.equipment.application;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes expired high-volume rows in bounded transactions so gateway inserts stay responsive. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "qms.equipment-telemetry.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EquipmentTelemetryRetentionScheduler {
    private static final String DELETE_RAW_SQL = """
        DELETE FROM qms_equipment_telemetry WHERE received_at < ?
          AND (event_type <> 'FLYING_PROBE_TEST_COUNTERS'
               OR EXISTS (SELECT 1 FROM qms_flying_probe_projection projection
                          WHERE projection.telemetry_id = qms_equipment_telemetry.id
                            AND projection.tenant_code = qms_equipment_telemetry.tenant_code))
        LIMIT
        """;
    private static final String DELETE_MINUTE_SQL = "DELETE FROM qms_equipment_telemetry_minute_summary WHERE bucket_start < ? LIMIT ";

    private final JdbcTemplate jdbcTemplate;

    @Value("${qms.equipment-telemetry.retention.raw-days:7}")
    private int rawRetentionDays;
    @Value("${qms.equipment-telemetry.retention.minute-summary-days:365}")
    private int minuteSummaryRetentionDays;
    @Value("${qms.equipment-telemetry.retention.cleanup-batch-size:10000}")
    private int cleanupBatchSize;
    @Value("${qms.equipment-telemetry.retention.cleanup-max-batches:30}")
    private int cleanupMaxBatches;

    @Scheduled(cron = "${qms.equipment-telemetry.retention.cleanup-cron:0 15 * * * *}")
    public void removeExpiredRecords() {
        LocalDateTime now = LocalDateTime.now();
        int rawDeleted = deleteInBatches(DELETE_RAW_SQL, now.minusDays(rawRetentionDays));
        int minuteDeleted = deleteInBatches(DELETE_MINUTE_SQL, now.minusDays(minuteSummaryRetentionDays));
        if (rawDeleted > 0 || minuteDeleted > 0) {
            log.info("设备遥测保留清理完成：删除秒级原始记录 {} 条、分钟汇总 {} 条", rawDeleted, minuteDeleted);
        }
    }

    private int deleteInBatches(String sqlPrefix, LocalDateTime cutoff) {
        int deleted = 0;
        int batchSize = Math.max(100, cleanupBatchSize);
        int maxBatches = Math.max(1, cleanupMaxBatches);
        for (int batch = 0; batch < maxBatches; batch++) {
            int count = jdbcTemplate.update(sqlPrefix + batchSize, cutoff);
            deleted += count;
            if (count < batchSize) break;
        }
        return deleted;
    }
}
