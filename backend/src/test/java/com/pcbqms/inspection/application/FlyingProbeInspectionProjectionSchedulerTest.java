package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.inspection.domain.FlyingProbeProjection;
import com.pcbqms.inspection.infrastructure.persistence.FlyingProbeProjectionMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FlyingProbeInspectionProjectionSchedulerTest {
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private FlyingProbeProjectionMapper projectionMapper;
    @Mock private FlyingProbeInspectionProjectionService projectionService;

    @Test
    void givesRecentFilesPriorityAndKeepsOlderBacklogMoving() {
        FlyingProbeProjection recentRetry = projection("recent-retry");
        FlyingProbeProjection olderRetry = projection("older-retry");
        EquipmentTelemetryRecord recent = telemetry("recent");
        EquipmentTelemetryRecord older = telemetry("older");
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class))).thenReturn(List.of("tenant-a"));
        when(projectionMapper.selectRecentRetryable(eq("tenant-a"), any(), any(), eq(75)))
            .thenReturn(List.of(recentRetry));
        when(projectionMapper.selectOlderRetryable(eq("tenant-a"), any(), any(), eq(99)))
            .thenReturn(List.of(olderRetry));
        when(projectionMapper.selectRecentUnprojected(eq("tenant-a"), any(), eq(75)))
            .thenReturn(List.of(recent));
        when(projectionMapper.selectOlderUnprojected(eq("tenant-a"), any(), eq(99)))
            .thenReturn(List.of(older));
        var scheduler = scheduler(100);
        LocalDateTime before = LocalDateTime.now().minusDays(7).truncatedTo(ChronoUnit.SECONDS);

        scheduler.projectPending();

        LocalDateTime after = LocalDateTime.now().minusDays(7).truncatedTo(ChronoUnit.SECONDS);
        var order = inOrder(projectionService);
        order.verify(projectionService).retry(recentRetry);
        order.verify(projectionService).retry(olderRetry);
        order.verify(projectionService).projectNew(recent);
        order.verify(projectionService).projectNew(older);
        ArgumentCaptor<LocalDateTime> cutoffs = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(projectionMapper).selectRecentUnprojected(eq("tenant-a"), cutoffs.capture(), eq(75));
        assertThat(cutoffs.getValue()).isBetween(before, after);
        verify(projectionMapper).selectOlderUnprojected(eq("tenant-a"), eq(cutoffs.getValue()), eq(99));
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unexpectedFailureIsQuarantinedAndDoesNotStopNextTelemetry() {
        EquipmentTelemetryRecord broken = new EquipmentTelemetryRecord();
        broken.setId("broken");
        EquipmentTelemetryRecord next = new EquipmentTelemetryRecord();
        next.setId("next");
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class))).thenReturn(List.of("tenant-a"));
        when(projectionMapper.selectRecentUnprojected(eq("tenant-a"), any(), eq(75)))
            .thenReturn(List.of(broken, next));
        doThrow(new IllegalStateException("transient database issue")).when(projectionService).projectNew(broken);
        var scheduler = scheduler(100);

        scheduler.projectPending();

        verify(projectionService).markUnexpectedFailure(broken);
        verify(projectionService).projectNew(next);
        assertThatThrownBy(TenantContext::tenantCode).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void saturatedRecentQueueStillReservesCapacityForOlderTelemetry() {
        EquipmentTelemetryRecord recent = telemetry("recent");
        EquipmentTelemetryRecord older = telemetry("older");
        when(jdbcTemplate.queryForList(any(String.class), eq(String.class))).thenReturn(List.of("tenant-a"));
        when(projectionMapper.selectRecentUnprojected(eq("tenant-a"), any(), eq(75)))
            .thenReturn(Collections.nCopies(75, recent));
        when(projectionMapper.selectOlderUnprojected(eq("tenant-a"), any(), eq(25)))
            .thenReturn(List.of(older));

        scheduler(100).projectPending();

        verify(projectionMapper).selectOlderUnprojected(eq("tenant-a"), any(), eq(25));
        verify(projectionService).projectNew(older);
    }

    private FlyingProbeInspectionProjectionScheduler scheduler(int batchSize) {
        var scheduler = new FlyingProbeInspectionProjectionScheduler(jdbcTemplate, projectionMapper, projectionService);
        ReflectionTestUtils.setField(scheduler, "configuredBatchSize", batchSize);
        ReflectionTestUtils.setField(scheduler, "rawRetentionDays", 7);
        return scheduler;
    }

    private FlyingProbeProjection projection(String id) {
        FlyingProbeProjection projection = new FlyingProbeProjection();
        projection.setTelemetryId(id);
        return projection;
    }

    private EquipmentTelemetryRecord telemetry(String id) {
        EquipmentTelemetryRecord telemetry = new EquipmentTelemetryRecord();
        telemetry.setId(id);
        return telemetry;
    }
}
