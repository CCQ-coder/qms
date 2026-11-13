package com.pcbqms.inspection.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class FlyingProbeProjectionMapperSqlTest {
    @Test
    void gyOldProjectionsAreExcludedFromListsAndRetries() {
        Configuration configuration = new Configuration();
        configuration.addMapper(FlyingProbeProjectionMapper.class);
        Map<String, Object> parameters = new HashMap<>(Map.of("tenantCode", "customer-b", "limit", 100,
            "pageSize", 50, "offset", 0, "now", LocalDateTime.of(2026, 11, 13, 9, 0),
            "cutoff", LocalDateTime.of(2026, 11, 6, 9, 0), "telemetryId", "old-probe"));

        for (String method : new String[] {"selectRetryableGroups", "countRetryableGroups",
            "selectRecentUnprojected", "selectOlderUnprojected", "selectRecentRetryable", "selectOlderRetryable"}) {
            String sql = configuration.getMappedStatement(FlyingProbeProjectionMapper.class.getName() + "." + method)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
            assertThat(sql).as(method).contains("'customer-b'", "2025-10-15", "device_time >=");
        }
    }

    @Test
    void retryableListAndCountUseTheSameSearchConditions() {
        Configuration configuration = new Configuration();
        configuration.addMapper(FlyingProbeProjectionMapper.class);
        Map<String, Object> parameters = new HashMap<>(Map.of(
            "tenantCode", "customer-b", "workOrderNo", "WO-1", "internalProductNo", "02PT42777A01",
            "pageSize", 50, "offset", 0));
        parameters.put("fromTime", LocalDateTime.of(2026, 11, 1, 0, 0));
        parameters.put("toTime", LocalDateTime.of(2026, 11, 9, 23, 59, 59));

        for (String method : new String[] {"selectRetryableGroups", "countRetryableGroups"}) {
            String sql = configuration.getMappedStatement(FlyingProbeProjectionMapper.class.getName() + "." + method)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
            assertThat(sql).contains("p.material_no LIKE CONCAT('%', ?, '%')",
                "COALESCE(r.work_order_no, p.work_order_no) LIKE CONCAT('%', ?, '%')",
                "p.device_time >= ?", "p.device_time <= ?");
        }
    }
}
