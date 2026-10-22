package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class EquipmentTelemetryRetentionSchedulerTest {
    @Test
    void rawCleanupSeparatesLimitFromBatchSize() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        EquipmentTelemetryRetentionScheduler scheduler = new EquipmentTelemetryRetentionScheduler(jdbcTemplate);
        ReflectionTestUtils.setField(scheduler, "cleanupBatchSize", 100);
        ReflectionTestUtils.setField(scheduler, "cleanupMaxBatches", 1);
        when(jdbcTemplate.update(anyString(), any(LocalDateTime.class))).thenReturn(0);

        scheduler.removeExpiredRecords();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(sql.capture(), any(LocalDateTime.class));
        assertThat(sql.getAllValues().getFirst()).containsPattern("\\bLIMIT\\s+100\\b");
    }

    @Test
    void rawCleanupKeepsFlyingProbeEventsUntilTheirProjectionIsDurable() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        EquipmentTelemetryRetentionScheduler scheduler = new EquipmentTelemetryRetentionScheduler(jdbcTemplate);
        ReflectionTestUtils.setField(scheduler, "rawRetentionDays", 7);
        ReflectionTestUtils.setField(scheduler, "minuteSummaryRetentionDays", 365);
        ReflectionTestUtils.setField(scheduler, "cleanupBatchSize", 100);
        ReflectionTestUtils.setField(scheduler, "cleanupMaxBatches", 1);
        when(jdbcTemplate.update(anyString(), any(LocalDateTime.class))).thenReturn(0);

        scheduler.removeExpiredRecords();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(sql.capture(), any(LocalDateTime.class));
        assertThat(sql.getAllValues().getFirst())
            .contains("event_type <> 'FLYING_PROBE_TEST_COUNTERS'")
            .contains("EXISTS (SELECT 1 FROM qms_flying_probe_projection")
            .contains("projection.telemetry_id = qms_equipment_telemetry.id")
            .contains("projection.tenant_code = qms_equipment_telemetry.tenant_code");
    }
}
