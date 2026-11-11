package com.pcbqms.equipment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FinishedTestDeviceSummaryService {
    private final JdbcTemplate jdbcTemplate;
    private final WorkOrderMapper workOrderMapper;
    private final InspectionAccessScopeService accessScopeService;

    public record Sample(String id, String equipmentId, String materialNo, String workOrderNo,
                         LocalDateTime deviceTime, long tested, long bad, boolean cumulative) { }

    public record Summary(LocalDate inspectionDate, String workOrderNo, String internalProductNo,
                          String pcbMaterialNo, long totalCount, long goodCount, long badCount, double passRate) { }
    public record DeviceSummary(LocalDate inspectionDate, String equipmentCode, String workOrderNo,
                                String internalProductNo, String pcbMaterialNo,
                                long totalCount, long goodCount, long badCount, double passRate) { }

    public record ModelMetric(String model, long totalCount, long goodCount, long badCount, double passRate) { }
    public record InternalDefectMetric(String internalProductNo, long badCount) { }
    public record MonthlyAnalytics(String month, List<ModelMetric> byModel, List<InternalDefectMetric> topInternalDefects) { }
    public record MaterialBadCount(String materialNo, long badCount) { }
    public record DailyMaterialBadBoards(String date, List<MaterialBadCount> materials) { }
    private record Attributed(Sample sample, WorkOrder order, long tested, long bad) { }

    record Binding(String workOrderNo, LocalDate detectionDate) { }

    public List<Summary> listSummary() {
        accessScopeService.requireProcessAccess("FQC");
        return summariesForCurrentTenant();
    }

    public List<Summary> summariesForCurrentTenant() {
        return aggregate(samples(), workOrders());
    }

    public List<DeviceSummary> listDeviceSummary() {
        accessScopeService.requireProcessAccess("FQC");
        Map<String, String> equipmentCodes = jdbcTemplate.query(
            "SELECT id, asset_no FROM qms_equipment WHERE tenant_code = ?",
            rs -> {
                Map<String, String> result = new HashMap<>();
                while (rs.next()) result.put(rs.getString("id"), rs.getString("asset_no"));
                return result;
            }, TenantContext.tenantCode());
        return aggregateByDevice(samples(), workOrders(), equipmentCodes);
    }

    public MonthlyAnalytics monthlyAnalytics() {
        accessScopeService.requireProcessAccess("FQC");
        Map<String, String> models = jdbcTemplate.query(
            "SELECT id, model_name FROM qms_equipment WHERE tenant_code = ?",
            rs -> {
                Map<String, String> result = new HashMap<>();
                while (rs.next()) result.put(rs.getString("id"), rs.getString("model_name"));
                return result;
            }, TenantContext.tenantCode());
        return aggregateMonthly(samples(), workOrders(), models, YearMonth.now());
    }

    public DailyMaterialBadBoards todayBadBoards() {
        return badBoardsForDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    DailyMaterialBadBoards badBoardsForDate(LocalDate date) {
        accessScopeService.requireProcessAccess("FQC");
        List<MaterialBadCount> materials = jdbcTemplate.query("""
            SELECT COALESCE(NULLIF(TRIM(material_no), ''), '未填写料号') AS material_no,
                   SUM(delta_bad) AS bad_count
            FROM qms_flying_probe_projection
            WHERE tenant_code = ? AND device_time >= ? AND device_time < ?
              AND status = 'PROCESSED' AND delta_bad > 0
            GROUP BY COALESCE(NULLIF(TRIM(material_no), ''), '未填写料号')
            ORDER BY bad_count DESC, material_no
            """, (rs, row) -> new MaterialBadCount(rs.getString("material_no"), rs.getLong("bad_count")),
            TenantContext.tenantCode(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        return new DailyMaterialBadBoards(date.toString(), materials);
    }

    public void matchUnboundZhongbo() {
        List<WorkOrder> orders = workOrders();
        for (Sample sample : samples()) {
            if (!sample.cumulative() || StringUtils.hasText(sample.workOrderNo())) continue;
            WorkOrder order = resolveOrder(sample, orders);
            if (order != null) {
                jdbcTemplate.update("UPDATE qms_equipment_telemetry SET work_order_no = ? "
                    + "WHERE id = ? AND tenant_code = ? AND event_type = 'ZHONGBO_TEST_REPORT' "
                    + "AND (work_order_no IS NULL OR work_order_no = '')",
                    order.getWorkOrderNo(), sample.id(), TenantContext.tenantCode());
            }
        }
    }

    @Transactional
    public String bindZhongboWorkOrder(String telemetryId, String requestedWorkOrderNo) {
        accessScopeService.requireSupervisorOrAdministrator();
        if (!StringUtils.hasText(requestedWorkOrderNo) || requestedWorkOrderNo.trim().length() > 80) {
            throw new IllegalArgumentException("请选择有效的生产指令");
        }
        String tenant = TenantContext.tenantCode();
        WorkOrder order = workOrderMapper.selectOne(Wrappers.lambdaQuery(WorkOrder.class)
            .eq(WorkOrder::getTenantCode, tenant).eq(WorkOrder::getWorkOrderNo, requestedWorkOrderNo.trim()));
        if (order == null) throw new IllegalArgumentException("当前租户不存在所选生产指令");
        List<Binding> current = jdbcTemplate.query("""
            SELECT work_order_no, DATE(device_time) AS detection_date FROM qms_equipment_telemetry
            WHERE id = ? AND tenant_code = ? AND event_type = 'ZHONGBO_TEST_REPORT'
            """, (rs, row) -> new Binding(rs.getString("work_order_no"),
                rs.getDate("detection_date").toLocalDate()), telemetryId, tenant);
        if (current.isEmpty()) throw new IllegalArgumentException("当前租户不存在此设备记录");
        Binding binding = current.getFirst();
        if (order.getWorkOrderNo().equals(binding.workOrderNo())) return order.getWorkOrderNo();
        if (StringUtils.hasText(binding.workOrderNo())) {
            Integer approved = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM qms_nonconformity disposition
                JOIN qms_work_order work_order
                  ON work_order.id COLLATE utf8mb4_0900_ai_ci = disposition.work_order_id COLLATE utf8mb4_0900_ai_ci
                WHERE disposition.tenant_code = ? AND work_order.tenant_code = ?
                  AND work_order.work_order_no = ? AND disposition.detection_date = ?
                  AND disposition.source_type = 'DEVICE' AND disposition.status = 'APPROVED'
                """, Integer.class, tenant, tenant, binding.workOrderNo(), binding.detectionDate());
            if (approved != null && approved > 0) {
                throw new IllegalArgumentException("原生产指令的不良品处置单已审核，不能更改关联");
            }
        }
        int updated = jdbcTemplate.update("""
            UPDATE qms_equipment_telemetry SET work_order_no = ?
            WHERE id = ? AND tenant_code = ? AND event_type = 'ZHONGBO_TEST_REPORT'
              AND work_order_no <=> ?
            """, order.getWorkOrderNo(), telemetryId, tenant, binding.workOrderNo());
        if (updated != 1) throw new IllegalArgumentException("设备记录关联已变化，请刷新后重试");
        return order.getWorkOrderNo();
    }

    private List<WorkOrder> workOrders() {
        return workOrderMapper.selectList(Wrappers.query(WorkOrder.class)
            .eq("tenant_code", TenantContext.tenantCode())
            .select("work_order_no", "internal_product_no", "material_no", "released_at"));
    }

    private List<Sample> samples() {
        // shortcut: scans retained device history, materialize per-record deltas when report volume grows.
        String tenant = TenantContext.tenantCode();
        List<Sample> samples = new ArrayList<>(jdbcTemplate.query("""
            SELECT telemetry_id AS id, equipment_id, material_no, work_order_no, device_time,
                   delta_tested AS tested, delta_bad AS bad
            FROM qms_flying_probe_projection
            WHERE tenant_code = ? AND status = 'PROCESSED' AND delta_tested > 0
            """, (rs, row) -> new Sample(rs.getString("id"), rs.getString("equipment_id"),
                rs.getString("material_no"), rs.getString("work_order_no"),
                rs.getTimestamp("device_time").toLocalDateTime(), rs.getLong("tested"), rs.getLong("bad"), false), tenant));
        samples.addAll(jdbcTemplate.query("""
            SELECT telemetry.id, telemetry.equipment_id, telemetry.material_no, telemetry.work_order_no, telemetry.device_time,
                   CAST(JSON_UNQUOTE(JSON_EXTRACT(telemetry.raw_payload, '$.attributes.PanelsTested')) AS UNSIGNED) AS tested,
                   CAST(JSON_UNQUOTE(JSON_EXTRACT(telemetry.raw_payload, '$.attributes.ErrorTested')) AS UNSIGNED) AS bad
            FROM qms_equipment_telemetry telemetry
            JOIN qms_equipment equipment
              ON equipment.id COLLATE utf8mb4_0900_ai_ci = telemetry.equipment_id COLLATE utf8mb4_0900_ai_ci
             AND equipment.tenant_code COLLATE utf8mb4_0900_ai_ci = telemetry.tenant_code COLLATE utf8mb4_0900_ai_ci
            WHERE telemetry.tenant_code = ? AND telemetry.event_type = 'ZHONGBO_TEST_REPORT'
              AND equipment.equipment_type = '成品测试设备'
            """, (rs, row) -> new Sample(rs.getString("id"), rs.getString("equipment_id"),
                rs.getString("material_no"), rs.getString("work_order_no"),
                rs.getTimestamp("device_time").toLocalDateTime(), rs.getLong("tested"), rs.getLong("bad"), true), tenant));
        return samples;
    }

    static List<Summary> aggregate(List<Sample> samples, List<WorkOrder> orders) {
        Map<String, long[]> totals = new HashMap<>();
        Map<String, Attributed> representatives = new HashMap<>();
        for (Attributed item : attributed(samples, orders, false)) {
            Sample sample = item.sample();
            String key = sample.deviceTime().toLocalDate() + "\u0000" + item.order().getWorkOrderNo();
            long[] sum = totals.computeIfAbsent(key, ignored -> new long[2]);
            sum[0] += item.tested();
            sum[1] += item.bad();
            representatives.putIfAbsent(key, item);
        }
        return totals.entrySet().stream().map(entry -> {
            Attributed item = representatives.get(entry.getKey());
            long tested = entry.getValue()[0];
            long bad = entry.getValue()[1];
            return new Summary(item.sample().deviceTime().toLocalDate(), item.order().getWorkOrderNo(),
                item.order().getInternalProductNo(), item.sample().materialNo(), tested, tested - bad, bad,
                Math.round((tested - bad) * 10000.0 / tested) / 100.0);
        }).sorted(Comparator.comparing(Summary::inspectionDate).reversed().thenComparing(Summary::workOrderNo)).toList();
    }

    static List<DeviceSummary> aggregateByDevice(List<Sample> samples, List<WorkOrder> orders,
                                                 Map<String, String> equipmentCodes) {
        Map<String, long[]> totals = new HashMap<>();
        Map<String, Attributed> representatives = new HashMap<>();
        for (Attributed item : attributed(samples, orders, false)) {
            Sample sample = item.sample();
            String key = sample.deviceTime().toLocalDate() + "\u0000" + sample.equipmentId() + "\u0000"
                + item.order().getWorkOrderNo() + "\u0000" + Objects.toString(sample.materialNo(), "");
            long[] sum = totals.computeIfAbsent(key, ignored -> new long[2]);
            sum[0] += item.tested();
            sum[1] += item.bad();
            representatives.putIfAbsent(key, item);
        }
        return totals.entrySet().stream().map(entry -> {
            Attributed item = representatives.get(entry.getKey());
            Sample sample = item.sample();
            long tested = entry.getValue()[0];
            long bad = entry.getValue()[1];
            String assetNo = equipmentCodes.get(sample.equipmentId());
            return new DeviceSummary(sample.deviceTime().toLocalDate(),
                StringUtils.hasText(assetNo) ? assetNo : Objects.toString(sample.equipmentId(), "未关联设备"),
                item.order().getWorkOrderNo(),
                item.order().getInternalProductNo(), sample.materialNo(), tested, tested - bad, bad,
                Math.round((tested - bad) * 10000.0 / tested) / 100.0);
        }).sorted(Comparator.comparing(DeviceSummary::inspectionDate).reversed()
            .thenComparing(DeviceSummary::equipmentCode).thenComparing(DeviceSummary::workOrderNo)).toList();
    }

    static MonthlyAnalytics aggregateMonthly(List<Sample> samples, List<WorkOrder> orders,
                                              Map<String, String> equipmentModels, YearMonth month) {
        Map<String, long[]> byModel = new HashMap<>();
        Map<String, Long> byInternal = new HashMap<>();
        for (Attributed item : attributed(samples, orders, true)) {
            if (!YearMonth.from(item.sample().deviceTime()).equals(month)) continue;
            String model = equipmentModels.get(item.sample().equipmentId());
            if (!StringUtils.hasText(model)) model = "未填写型号";
            long[] counts = byModel.computeIfAbsent(model, ignored -> new long[2]);
            counts[0] += item.tested();
            counts[1] += item.bad();
            if (item.bad() > 0 && item.order() != null) {
                String internal = item.order().getInternalProductNo();
                byInternal.merge(StringUtils.hasText(internal) ? internal : "未填写厂内编号", item.bad(), Long::sum);
            }
        }
        List<ModelMetric> models = byModel.entrySet().stream().map(entry -> {
            long tested = entry.getValue()[0];
            long bad = entry.getValue()[1];
            return new ModelMetric(entry.getKey(), tested, tested - bad, bad,
                Math.round((tested - bad) * 10000.0 / tested) / 100.0);
        }).sorted(Comparator.comparingLong(ModelMetric::totalCount).reversed().thenComparing(ModelMetric::model)).toList();
        List<InternalDefectMetric> topInternal = byInternal.entrySet().stream()
            .map(entry -> new InternalDefectMetric(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(InternalDefectMetric::badCount).reversed()
                .thenComparing(InternalDefectMetric::internalProductNo))
            .limit(10).toList();
        return new MonthlyAnalytics(month.toString(), models, topInternal);
    }

    private static List<Attributed> attributed(List<Sample> samples, List<WorkOrder> orders, boolean includeUnbound) {
        Map<String, WorkOrder> byNumber = new HashMap<>();
        orders.forEach(order -> byNumber.put(order.getWorkOrderNo(), order));
        Map<String, long[]> previous = new HashMap<>();
        List<Attributed> result = new ArrayList<>();
        for (Sample sample : samples.stream().sorted(Comparator.comparing(Sample::deviceTime).thenComparing(Sample::id)).toList()) {
            if (sample.tested() < 0 || sample.bad() < 0 || sample.bad() > sample.tested()) continue;
            long tested = sample.tested();
            long bad = sample.bad();
            if (sample.cumulative()) {
                String counterKey = sample.equipmentId() + "\u0000" + sample.materialNo();
                long[] last = previous.put(counterKey, new long[] {tested, bad});
                if (last != null) {
                    tested = sample.tested() >= last[0] ? sample.tested() - last[0] : sample.tested();
                    bad = sample.bad() >= last[1] ? sample.bad() - last[1] : sample.bad();
                }
            }
            if (bad > tested || tested == 0) continue;
            WorkOrder order = StringUtils.hasText(sample.workOrderNo())
                ? byNumber.get(sample.workOrderNo()) : resolveOrder(sample, orders);
            if (order == null && !includeUnbound) continue;
            result.add(new Attributed(sample, order, tested, bad));
        }
        return result;
    }

    private static WorkOrder resolveOrder(Sample sample, List<WorkOrder> orders) {
        if (!StringUtils.hasText(sample.materialNo())) return null;
        List<WorkOrder> candidates = orders.stream()
            .filter(order -> (StringUtils.hasText(order.getInternalProductNo())
                    && sample.materialNo().trim().equalsIgnoreCase(order.getInternalProductNo().trim())
                || StringUtils.hasText(order.getMaterialNo())
                    && sample.materialNo().trim().equalsIgnoreCase(order.getMaterialNo().trim()))
                && order.getReleasedAt() != null && !order.getReleasedAt().isAfter(sample.deviceTime().toLocalDate()))
            .toList();
        LocalDate latest = candidates.stream().map(WorkOrder::getReleasedAt)
            .max(LocalDate::compareTo).orElse(null);
        List<WorkOrder> latestOrders = candidates.stream()
            .filter(order -> Objects.equals(order.getReleasedAt(), latest)).toList();
        return latestOrders.size() == 1 ? latestOrders.getFirst() : null;
    }
}
