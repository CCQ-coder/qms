package com.pcbqms.inspection.application;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.FinishedTestDataWindow;
import com.pcbqms.inspection.domain.FlyingProbeProjection;
import com.pcbqms.inspection.infrastructure.persistence.FlyingProbeProjectionMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Projects a bounded batch of durable raw messages independently of telemetry ingestion. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "qms.inspection.flying-probe", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlyingProbeInspectionProjectionScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final FlyingProbeProjectionMapper projectionMapper;
    private final FlyingProbeInspectionProjectionService projectionService;

    @Value("${qms.inspection.flying-probe.batch-size:100}")
    private int configuredBatchSize;
    @Value("${qms.equipment-telemetry.retention.raw-days:7}")
    private int rawRetentionDays;
    @Value("${qms.inspection.flying-probe.retry-workers:4}")
    private int retryWorkers = 4;

    @Scheduled(fixedDelayString = "${qms.inspection.flying-probe.fixed-delay-ms:5000}")
    public void projectPending() {
        int batchSize = Math.clamp(configuredBatchSize, 1, 200);
        int recentLimit = Math.max(1, batchSize - Math.max(1, batchSize / 4));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime recentCutoff = now.minusDays(rawRetentionDays).truncatedTo(ChronoUnit.SECONDS);
        if (recentCutoff.isBefore(FinishedTestDataWindow.START)) recentCutoff = FinishedTestDataWindow.START;
        for (String tenantCode : jdbcTemplate.queryForList(
            "SELECT code FROM qms_tenant WHERE status = 'ACTIVE' ORDER BY code", String.class)) {
            TenantContext.set(tenantCode, "admin");
            try {
                var recentRetryable = projectionMapper.selectRecentRetryable(
                    tenantCode, now, recentCutoff, recentLimit);
                var olderRetryable = projectionMapper.selectOlderRetryable(
                    tenantCode, now, recentCutoff, FinishedTestDataWindow.START,
                    Math.max(1, batchSize - recentRetryable.size()));
                retryPending(tenantCode, Stream.concat(recentRetryable.stream(), olderRetryable.stream()).toList());
                var recentUnprojected = projectionMapper.selectRecentUnprojected(
                    tenantCode, recentCutoff, recentLimit);
                var olderUnprojected = projectionMapper.selectOlderUnprojected(
                    tenantCode, recentCutoff, FinishedTestDataWindow.START,
                    Math.max(1, batchSize - recentUnprojected.size()));
                for (var telemetry : Stream.concat(recentUnprojected.stream(), olderUnprojected.stream()).toList()) {
                    try {
                        projectionService.projectNew(telemetry);
                    } catch (RuntimeException exception) {
                        log.warn("飞针遥测投影失败，租户={}，遥测={}", tenantCode, telemetry.getId(), exception);
                        try {
                            projectionService.markUnexpectedFailure(telemetry);
                        } catch (RuntimeException markingFailure) {
                            log.error("飞针投影失败状态无法保存，租户={}，遥测={}", tenantCode,
                                telemetry.getId(), markingFailure);
                        }
                    }
                }
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void retryPending(String tenantCode, List<FlyingProbeProjection> pending) {
        var byRun = pending.stream().collect(Collectors.groupingBy(
            row -> row.getRunKey() == null ? "equipment:" + row.getEquipmentId() : row.getRunKey(),
            LinkedHashMap::new, Collectors.toList()));
        if (byRun.size() <= 1 || retryWorkers <= 1) {
            pending.forEach(row -> retryOne(tenantCode, row));
            return;
        }
        int workers = Math.clamp(retryWorkers, 1, 8);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            var jobs = byRun.values().stream().map(group -> CompletableFuture.runAsync(() -> {
                TenantContext.set(tenantCode, "admin");
                try {
                    group.sort(Comparator.comparing(FlyingProbeProjection::getDeviceTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
                    group.forEach(row -> retryOne(tenantCode, row));
                } finally {
                    TenantContext.clear();
                }
            }, executor)).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(jobs).join();
        }
    }

    private void retryOne(String tenantCode, FlyingProbeProjection pending) {
        try {
            projectionService.retry(pending);
        } catch (RuntimeException exception) {
            log.warn("飞针历史投影重试失败，租户={}，遥测={}", tenantCode, pending.getTelemetryId(), exception);
            try {
                projectionService.markRetryFailure(pending);
            } catch (RuntimeException markingFailure) {
                log.error("飞针历史投影失败状态无法保存，租户={}，遥测={}", tenantCode,
                    pending.getTelemetryId(), markingFailure);
            }
        }
    }
}
