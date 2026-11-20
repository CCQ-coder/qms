package com.pcbqms.equipment.application;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.FinishedTestDataWindow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinishedTestDeviceReportService {
    private final JdbcTemplate jdbcTemplate;
    private final InspectionAccessScopeService accessScopeService;

    public record DeviceSummary(LocalDate inspectionDate, String equipmentCode, String workOrderNo,
                                String internalProductNo, String pcbMaterialNo,
                                long totalCount, long goodCount, long badCount, double passRate) { }
    public record ModelMetric(String model, long totalCount, long goodCount, long badCount, double passRate) { }
    public record InternalDefectMetric(String internalProductNo, long badCount) { }
    public record MonthlyAnalytics(String month, List<ModelMetric> byModel,
                                   List<InternalDefectMetric> topInternalDefects) { }
    public record Settlement(String materialKey, String workOrderNo, String internalProductNo,
                             LocalDateTime firstDetectionAt, LocalDateTime lastDetectionAt,
                             LocalDateTime settledAt, int equipmentCount, long tested, long good, long bad) { }
    record Sample(String equipmentId, String materialNo, String workOrderNo,
                  LocalDateTime deviceTime, long tested, long bad) { }
    record SettlementCandidate(String materialKey, String workOrderNo, LocalDateTime firstDetectionAt,
                               LocalDateTime lastDetectionAt, int equipmentCount, long tested, long bad) { }

    public List<Settlement> listSettlements() {
        accessScopeService.requireProcessAccess("FQC");
        return jdbcTemplate.query("""
            SELECT material_key, work_order_no, internal_product_no, first_detection_at,
                   last_detection_at, settled_at, equipment_count, tested_count, good_count, bad_count
            FROM qms_finished_test_settlement
            WHERE tenant_code = ?
            ORDER BY last_detection_at DESC, material_key
            LIMIT 100
            """, (rs, row) -> new Settlement(rs.getString("material_key"), rs.getString("work_order_no"),
                rs.getString("internal_product_no"), rs.getTimestamp("first_detection_at").toLocalDateTime(),
                rs.getTimestamp("last_detection_at").toLocalDateTime(),
                rs.getTimestamp("settled_at").toLocalDateTime(), rs.getInt("equipment_count"),
                rs.getLong("tested_count"), rs.getLong("good_count"), rs.getLong("bad_count")),
            TenantContext.tenantCode());
    }

    // Commit each settlement separately so the sweep cannot hold work-order locks against ERP sync.
    public void settleIdleMaterials(LocalDateTime now) {
        String tenant = TenantContext.tenantCode();
        // shortcut: scans processed projections each minute, add incremental settlement state if volume grows.
        List<Sample> samples = jdbcTemplate.query("""
            SELECT equipment_id, material_no, work_order_no, device_time, delta_tested, delta_bad
            FROM qms_flying_probe_projection
            WHERE tenant_code = ? AND status = 'PROCESSED' AND delta_tested > 0 AND device_time >= ?
            """, (rs, row) -> new Sample(rs.getString("equipment_id"), rs.getString("material_no"),
                rs.getString("work_order_no"), rs.getTimestamp("device_time").toLocalDateTime(),
                rs.getLong("delta_tested"), rs.getLong("delta_bad")), tenant, FinishedTestDataWindow.START);
        for (SettlementCandidate candidate : planIdleSettlements(samples, now)) {
            Integer unresolved = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM qms_flying_probe_projection
                WHERE tenant_code = ? AND UPPER(TRIM(material_no)) = ?
                  AND device_time >= ? AND device_time < ? AND status <> 'PROCESSED'
                """, Integer.class, tenant, candidate.materialKey(), candidate.firstDetectionAt(),
                candidate.lastDetectionAt().plusHours(12));
            if (unresolved != null && unresolved > 0) continue;
            Integer unprojected = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM qms_equipment_telemetry telemetry
                WHERE telemetry.tenant_code = ? AND UPPER(TRIM(telemetry.material_no)) = ?
                  AND telemetry.event_type = 'FLYING_PROBE_TEST_COUNTERS'
                  AND telemetry.device_time >= ? AND telemetry.device_time < ?
                  AND NOT EXISTS (SELECT 1 FROM qms_flying_probe_projection projection
                                  WHERE projection.telemetry_id = telemetry.id)
                """, Integer.class, tenant, candidate.materialKey(), candidate.firstDetectionAt(),
                candidate.lastDetectionAt().plusHours(12));
            if (unprojected != null && unprojected > 0) continue;
            jdbcTemplate.update("""
                INSERT INTO qms_finished_test_settlement (
                    tenant_code, material_key, work_order_no, internal_product_no,
                    first_detection_at, last_detection_at, settled_at, equipment_count,
                    tested_count, good_count, bad_count
                ) SELECT ?, ?, ?, w.internal_product_no, ?, ?, ?, ?, ?, ?, ?
                  FROM qms_work_order w WHERE w.tenant_code = ? AND w.work_order_no = ?
                ON DUPLICATE KEY UPDATE
                    work_order_no = VALUES(work_order_no), internal_product_no = VALUES(internal_product_no),
                    last_detection_at = VALUES(last_detection_at), equipment_count = VALUES(equipment_count),
                    tested_count = VALUES(tested_count), good_count = VALUES(good_count), bad_count = VALUES(bad_count)
                """, tenant, candidate.materialKey(), candidate.workOrderNo(), candidate.firstDetectionAt(),
                candidate.lastDetectionAt(), now, candidate.equipmentCount(), candidate.tested(),
                candidate.tested() - candidate.bad(), candidate.bad(), tenant, candidate.workOrderNo());
        }
    }

    static List<SettlementCandidate> planIdleSettlements(List<Sample> samples, LocalDateTime now) {
        Map<String, List<Sample>> byMaterial = new HashMap<>();
        for (Sample sample : samples) {
            if (sample.materialNo() != null && !sample.materialNo().isBlank()) {
                byMaterial.computeIfAbsent(sample.materialNo().trim().toUpperCase(Locale.ROOT), ignored -> new ArrayList<>()).add(sample);
            }
        }
        List<SettlementCandidate> result = new ArrayList<>();
        byMaterial.forEach((material, history) -> {
            history.sort(Comparator.comparing(Sample::deviceTime));
            int start = 0;
            for (int end = 0; end < history.size(); end++) {
                if (end + 1 < history.size() && Duration.between(history.get(end).deviceTime(),
                    history.get(end + 1).deviceTime()).toHours() < 12) continue;
                Sample last = history.get(end);
                if (!last.deviceTime().plusHours(12).isAfter(now)) {
                    String order = last.workOrderNo();
                    long tested = 0;
                    long bad = 0;
                    Set<String> equipmentIds = new HashSet<>();
                    boolean conflict = order == null || order.isBlank();
                    for (int index = start; index <= end; index++) {
                        Sample sample = history.get(index);
                        if (!Objects.equals(order, sample.workOrderNo())) conflict = true;
                        tested += sample.tested();
                        bad += sample.bad();
                        equipmentIds.add(sample.equipmentId());
                    }
                    if (!conflict && tested > 0) result.add(new SettlementCandidate(material, order,
                        history.get(start).deviceTime(), last.deviceTime(), equipmentIds.size(), tested, bad));
                }
                start = end + 1;
            }
        });
        return result;
    }

    public List<DeviceSummary> listDeviceSummary() {
        accessScopeService.requireProcessAccess("FQC");
        return jdbcTemplate.query("""
            SELECT DATE(p.device_time) AS inspection_date,
                   COALESCE(NULLIF(e.asset_no, ''), p.equipment_id) AS equipment_code,
                   p.work_order_no, w.internal_product_no, p.material_no AS pcb_material_no,
                   SUM(p.delta_tested) AS total_count, SUM(p.delta_bad) AS bad_count
            FROM qms_flying_probe_projection p
            JOIN qms_work_order w ON w.tenant_code COLLATE utf8mb4_0900_ai_ci = p.tenant_code COLLATE utf8mb4_0900_ai_ci
                AND w.work_order_no COLLATE utf8mb4_0900_ai_ci = p.work_order_no COLLATE utf8mb4_0900_ai_ci
            LEFT JOIN qms_equipment e ON e.tenant_code COLLATE utf8mb4_0900_ai_ci = p.tenant_code COLLATE utf8mb4_0900_ai_ci
                AND e.id COLLATE utf8mb4_0900_ai_ci = p.equipment_id COLLATE utf8mb4_0900_ai_ci
            WHERE p.tenant_code = ? AND p.status = 'PROCESSED' AND p.delta_tested > 0
              AND p.device_time >= ?
            GROUP BY DATE(p.device_time), COALESCE(NULLIF(e.asset_no, ''), p.equipment_id),
                     p.work_order_no, w.internal_product_no, p.material_no
            ORDER BY inspection_date DESC, equipment_code, p.work_order_no
            """, (rs, row) -> {
                long tested = rs.getLong("total_count");
                long bad = rs.getLong("bad_count");
                return new DeviceSummary(rs.getDate("inspection_date").toLocalDate(),
                    rs.getString("equipment_code"), rs.getString("work_order_no"),
                    rs.getString("internal_product_no"), rs.getString("pcb_material_no"),
                    tested, tested - bad, bad, passRate(tested, bad));
            }, TenantContext.tenantCode(), FinishedTestDataWindow.START);
    }

    public MonthlyAnalytics monthlyAnalytics() {
        accessScopeService.requireProcessAccess("FQC");
        YearMonth month = YearMonth.now(ZoneId.of("Asia/Shanghai"));
        String tenant = TenantContext.tenantCode();
        List<ModelMetric> models = jdbcTemplate.query("""
            SELECT COALESCE(NULLIF(e.model_name, ''), NULLIF(e.manufacturer, ''), '未填写型号') AS model,
                   SUM(p.delta_tested) AS total_count, SUM(p.delta_bad) AS bad_count
            FROM qms_flying_probe_projection p
            LEFT JOIN qms_equipment e ON e.tenant_code COLLATE utf8mb4_0900_ai_ci = p.tenant_code COLLATE utf8mb4_0900_ai_ci
                AND e.id COLLATE utf8mb4_0900_ai_ci = p.equipment_id COLLATE utf8mb4_0900_ai_ci
            WHERE p.tenant_code = ? AND p.status = 'PROCESSED' AND p.delta_tested > 0
              AND p.device_time >= ? AND p.device_time < ?
            GROUP BY COALESCE(NULLIF(e.model_name, ''), NULLIF(e.manufacturer, ''), '未填写型号')
            ORDER BY total_count DESC, model
            """, (rs, row) -> {
                long tested = rs.getLong("total_count");
                long bad = rs.getLong("bad_count");
                return new ModelMetric(rs.getString("model"), tested, tested - bad, bad, passRate(tested, bad));
            }, tenant, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
        List<InternalDefectMetric> defects = jdbcTemplate.query("""
            SELECT COALESCE(NULLIF(w.internal_product_no, ''), '未填写厂内编号') AS internal_product_no,
                   SUM(p.delta_bad) AS bad_count
            FROM qms_flying_probe_projection p
            JOIN qms_work_order w ON w.tenant_code COLLATE utf8mb4_0900_ai_ci = p.tenant_code COLLATE utf8mb4_0900_ai_ci
                AND w.work_order_no COLLATE utf8mb4_0900_ai_ci = p.work_order_no COLLATE utf8mb4_0900_ai_ci
            WHERE p.tenant_code = ? AND p.status = 'PROCESSED' AND p.delta_bad > 0
              AND p.device_time >= ? AND p.device_time < ?
            GROUP BY COALESCE(NULLIF(w.internal_product_no, ''), '未填写厂内编号')
            ORDER BY bad_count DESC, internal_product_no
            LIMIT 10
            """, (rs, row) -> new InternalDefectMetric(rs.getString("internal_product_no"), rs.getLong("bad_count")),
            tenant, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
        return new MonthlyAnalytics(month.toString(), models, defects);
    }

    static double passRate(long tested, long bad) {
        return tested == 0 ? 0 : Math.round((tested - bad) * 10000.0 / tested) / 100.0;
    }
}
