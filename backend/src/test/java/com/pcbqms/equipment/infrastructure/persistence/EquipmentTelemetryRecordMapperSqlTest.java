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

    private String historySql(String eventType) {
        return historySql(eventType, null, null);
    }

    private String historySql(String eventType, String workOrderNo, String internalProductNo) {
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
        return configuration.getMappedStatement(EquipmentTelemetryRecordMapper.class.getName() + ".selectHistory")
            .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }
}
