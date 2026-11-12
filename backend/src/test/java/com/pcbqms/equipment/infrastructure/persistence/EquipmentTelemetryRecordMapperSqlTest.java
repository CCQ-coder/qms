package com.pcbqms.equipment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class EquipmentTelemetryRecordMapperSqlTest {
    @Test
    void flyingProbeHistoryOrdersAndPagesByDeviceTime() {
        String sql = historySql("FLYING_PROBE_TEST_COUNTERS");

        assertThat(sql).contains("device_time < ?", "device_time = ?", "ORDER BY device_time DESC, id DESC");
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
    void combinedFinishedTestHistorySelectsBothDeviceFeeds() {
        String sql = historySql("FINISHED_TEST");

        assertThat(sql).contains("event_type IN ('FLYING_PROBE_TEST_COUNTERS', 'ZHONGBO_TEST_REPORT')")
            .doesNotContain("event_type = ?");
    }

    @Test
    void finishedTestDateFilterUsesDeviceDetectionTimeWhileOtherTelemetryUsesReceiptTime() {
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
        Configuration configuration = new Configuration();
        configuration.addMapper(EquipmentTelemetryRecordMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantCode", "customer-b");
        parameters.put("eventType", eventType);
        parameters.put("workOrderNo", workOrderNo);
        parameters.put("internalProductNo", internalProductNo);
        if (withDates) {
            parameters.put("fromTime", LocalDateTime.of(2026, 11, 1, 0, 0));
            parameters.put("toTime", LocalDateTime.of(2026, 11, 9, 23, 59));
        }
        parameters.put("cursorTime", LocalDateTime.of(2026, 11, 1, 9, 0));
        parameters.put("cursorId", "probe-1");
        parameters.put("limit", 100);
        return configuration.getMappedStatement(EquipmentTelemetryRecordMapper.class.getName() + ".selectHistory")
            .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }
}
