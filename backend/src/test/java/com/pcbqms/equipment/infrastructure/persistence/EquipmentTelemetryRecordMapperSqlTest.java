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
    void flyingProbeHistorySearchesProductionOrderAndInternalNumberBeforePaging() {
        String sql = historySql("FLYING_PROBE_TEST_COUNTERS", "26100985", "P2L018680B");

        assertThat(sql).contains("work_order_no LIKE CONCAT('%', ?, '%')");
        assertThat(sql).contains("material_no LIKE CONCAT('%', ?, '%')");
        assertThat(sql.indexOf("work_order_no LIKE")).isLessThan(sql.indexOf("ORDER BY device_time"));
    }

    @Test
    void numberedHistoryCountsMatchingRowsBeforeLimitingTheSelectedPage() {
        String firstPageSql = historySql("FLYING_PROBE_TEST_COUNTERS", null, "P2L018680B", 0L, true);
        String laterPageSql = historySql("FLYING_PROBE_TEST_COUNTERS", null, null, 20L, false);

        assertThat(firstPageSql).contains("COUNT(*) OVER()", "LIMIT ? OFFSET ?");
        assertThat(firstPageSql.indexOf("material_no LIKE")).isLessThan(firstPageSql.indexOf("ORDER BY device_time"));
        assertThat(laterPageSql).contains("LIMIT ? OFFSET ?").doesNotContain("COUNT(*) OVER()");
    }

    private String historySql(String eventType) {
        return historySql(eventType, null, null);
    }

    private String historySql(String eventType, String workOrderNo, String internalProductNo) {
        return historySql(eventType, workOrderNo, internalProductNo, null, false);
    }

    private String historySql(String eventType, String workOrderNo, String internalProductNo,
                              Long offset, boolean includeTotal) {
        Configuration configuration = new Configuration();
        configuration.addMapper(EquipmentTelemetryRecordMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantCode", "customer-b");
        parameters.put("eventType", eventType);
        parameters.put("workOrderNo", workOrderNo);
        parameters.put("internalProductNo", internalProductNo);
        parameters.put("cursorTime", LocalDateTime.of(2026, 11, 1, 9, 0));
        parameters.put("cursorId", "probe-1");
        parameters.put("limit", 100);
        parameters.put("offset", offset);
        parameters.put("includeTotal", includeTotal);
        return configuration.getMappedStatement(EquipmentTelemetryRecordMapper.class.getName() + ".selectHistory")
            .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }
}
