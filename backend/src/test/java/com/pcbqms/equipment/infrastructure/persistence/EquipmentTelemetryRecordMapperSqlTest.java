package com.pcbqms.equipment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class EquipmentTelemetryRecordMapperSqlTest {
    @Test
    void flyingProbeHistoryOrdersAndPagesByServerReceiptTime() {
        String sql = historySql("FLYING_PROBE_TEST_COUNTERS");

        assertThat(sql).contains("received_at < ?", "received_at = ?", "ORDER BY received_at DESC, id DESC")
            .doesNotContain("AND (device_time < ?");
    }

    @Test
    void otherTelemetryStillOrdersAndPagesByReceivedTime() {
        String sql = historySql("LEGACY_MONITOR");

        assertThat(sql).contains("received_at < ?", "received_at = ?", "ORDER BY received_at DESC, id DESC");
    }

    @Test
    void historyFiltersByProductionOrderAndInternalProductNumber() {
        String sql = historySql("FLYING_PROBE_TEST_COUNTERS", "WO-1", "02PT42777A01");

        assertThat(sql).contains("work_order_no LIKE CONCAT('%', ?, '%')",
            "material_no LIKE CONCAT('%', ?, '%')",
            "FROM qms_flying_probe_projection projection");
    }

    @Test
    void historyFiltersCustomerOrderThroughTheLinkedProductionOrder() {
        String sql = historySql("FINISHED_TEST", null, null, false, "PI10102871");

        assertThat(sql).contains("work_order.customer_order_no LIKE CONCAT('%', ?, '%')",
            "FROM qms_flying_probe_projection projection", "qms_equipment_telemetry.work_order_no");
    }

    @Test
    void combinedFinishedTestHistorySelectsBothDeviceFeeds() {
        String sql = historySql("FINISHED_TEST");

        assertThat(sql).contains("event_type IN ('FLYING_PROBE_TEST_COUNTERS', 'ZHONGBO_TEST_REPORT')")
            .contains("ORDER BY device_time DESC, id DESC")
            .doesNotContain("event_type = ?");
    }

    @Test
    void combinedFinishedTestHistoryCanPageFromOldestDetectionTime() {
        String sql = historySql("FINISHED_TEST", null, null, false, null, true, null, true);
        assertThat(sql).contains("ORDER BY device_time ASC, id ASC", "LIMIT ? OFFSET ?");
    }

    @Test
    void numberedHistoryReturnsTotalAndOnlyTheRequestedPage() {
        String sql = historySql("FINISHED_TEST");
        String laterPageSql = historySql("FINISHED_TEST", null, null, false, null, false);

        assertThat(sql).contains("COUNT(*) OVER()", "LIMIT ? OFFSET ?",
            "event_type IN ('FLYING_PROBE_TEST_COUNTERS', 'ZHONGBO_TEST_REPORT')");
        assertThat(laterPageSql).contains("LIMIT ? OFFSET ?").doesNotContain("COUNT(*) OVER()");
    }

    @Test
    void numberedHistorySearchesAllRecordsBeforeCountingAndPaging() {
        String sql = historySql("FINISHED_TEST", null, null, false, null, true, "42946");
        assertThat(sql).contains("material_no LIKE CONCAT('%', ?, '%')", "source_record_no LIKE CONCAT('%', ?, '%')",
            "equipment.asset_no LIKE CONCAT('%', ?, '%')", "work_order.internal_product_no LIKE CONCAT('%', ?, '%')",
            "COUNT(*) OVER()", "LIMIT ? OFFSET ?");
    }

    @Test
    void gyHistoryAndRecentListsExcludePreCutoffFinishedTestRecordsBeforePaging() {
        String condition = "tenant_code <> 'customer-b' OR event_type NOT IN ('FLYING_PROBE_TEST_COUNTERS', 'ZHONGBO_TEST_REPORT') "
            + "OR device_time >= '2026-10-16'";
        assertThat(historySql("FINISHED_TEST")).contains(condition);
        assertThat(historySql("ZHONGBO_TEST_REPORT")).contains(condition);

        Configuration configuration = new Configuration();
        configuration.addMapper(EquipmentTelemetryRecordMapper.class);
        String recentSql = configuration.getMappedStatement(
            EquipmentTelemetryRecordMapper.class.getName() + ".selectRecentByTenant")
            .getBoundSql(Map.of("tenantCode", "customer-b", "limit", 100))
            .getSql().replaceAll("\\s+", " ");
        assertThat(recentSql).contains(condition);
    }

    @Test
    void latestFlyingProbeSnapshotsPickOnePerEquipmentWithoutAnArtificialReceiptCutoff() {
        Configuration configuration = new Configuration();
        configuration.addMapper(EquipmentTelemetryRecordMapper.class);
        String sql = configuration.getMappedStatement(
            EquipmentTelemetryRecordMapper.class.getName() + ".selectLatestFlyingProbeByEquipment")
            .getBoundSql(Map.of("tenantCode", "customer-b", "equipmentIds", List.of("equipment-1", "equipment-2")))
            .getSql().replaceAll("\\s+", " ");

        assertThat(sql).contains("event_type = 'FLYING_PROBE_TEST_COUNTERS'",
            "PARTITION BY equipment_id ORDER BY device_time DESC, received_at DESC, id DESC",
            "device_time >= '2026-10-16'", "equipment_id IN ( ? , ? )", "row_no = 1")
            .doesNotContain("received_at >= ?", "received_at <= ?");
    }

    @Test
    void finishedTestDateFilterUsesDeviceDetectionTimeWhileOtherTelemetryUsesReceiptTime() {
        assertThat(historySql("FLYING_PROBE_TEST_COUNTERS", null, null, true))
            .contains("received_at >= ?", "received_at <= ?")
            .doesNotContain("device_time >= ?", "device_time <= ?");
        assertThat(historySql("FINISHED_TEST", null, null, true))
            .contains("device_time >= ?", "device_time <= ?")
            .doesNotContain("received_at >= ?", "received_at <= ?");
        assertThat(historySql("ZHONGBO_TEST_REPORT", null, null, true))
            .contains("device_time >= ?", "device_time <= ?");
        assertThat(historySql("LEGACY_MONITOR", null, null, true))
            .contains("received_at >= ?", "received_at <= ?");
    }

    private String historySql(String eventType) {
        return historySql(eventType, null, null);
    }

    private String historySql(String eventType, String workOrderNo, String internalProductNo) {
        return historySql(eventType, workOrderNo, internalProductNo, false);
    }

    private String historySql(String eventType, String workOrderNo, String internalProductNo, boolean withDates) {
        return historySql(eventType, workOrderNo, internalProductNo, withDates, null);
    }

    private String historySql(String eventType, String workOrderNo, String internalProductNo,
                              boolean withDates, String customerOrderNo) {
        return historySql(eventType, workOrderNo, internalProductNo, withDates, customerOrderNo, true);
    }

    private String historySql(String eventType, String workOrderNo, String internalProductNo,
                              boolean withDates, String customerOrderNo, boolean includeTotal) {
        return historySql(eventType, workOrderNo, internalProductNo, withDates, customerOrderNo, includeTotal, null);
    }

    private String historySql(String eventType, String workOrderNo, String internalProductNo,
                              boolean withDates, String customerOrderNo, boolean includeTotal, String keyword) {
        return historySql(eventType, workOrderNo, internalProductNo, withDates, customerOrderNo, includeTotal, keyword, false);
    }

    private String historySql(String eventType, String workOrderNo, String internalProductNo,
                              boolean withDates, String customerOrderNo, boolean includeTotal, String keyword, boolean ascending) {
        Configuration configuration = new Configuration();
        configuration.addMapper(EquipmentTelemetryRecordMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantCode", "customer-b");
        parameters.put("eventType", eventType);
        parameters.put("workOrderNo", workOrderNo);
        parameters.put("internalProductNo", internalProductNo);
        parameters.put("customerOrderNo", customerOrderNo);
        if (withDates) {
            parameters.put("fromTime", LocalDateTime.of(2026, 11, 1, 0, 0));
            parameters.put("toTime", LocalDateTime.of(2026, 11, 9, 23, 59));
        }
        parameters.put("cursorTime", LocalDateTime.of(2026, 11, 1, 9, 0));
        parameters.put("cursorId", "probe-1");
        parameters.put("limit", 100);
        parameters.put("offset", 20L);
        parameters.put("includeTotal", includeTotal);
        parameters.put("keyword", keyword);
        parameters.put("ascending", ascending);
        return configuration.getMappedStatement(EquipmentTelemetryRecordMapper.class.getName() + ".selectHistory")
            .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }
}
