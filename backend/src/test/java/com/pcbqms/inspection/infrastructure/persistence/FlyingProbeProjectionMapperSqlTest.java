package com.pcbqms.inspection.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class FlyingProbeProjectionMapperSqlTest {
    @Test
    void retryableSearchFiltersGroupedRowsAndCountByProductionOrderAndInternalNumber() {
        Configuration configuration = new Configuration();
        configuration.addMapper(FlyingProbeProjectionMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantCode", "tenant-a");
        parameters.put("dataStart", LocalDateTime.of(2026, 10, 20, 0, 0));
        parameters.put("workOrderNo", "26100985");
        parameters.put("internalProductNo", "P2L018680B");
        parameters.put("pageSize", 50);
        parameters.put("offset", 0L);

        for (String statement : new String[] { "selectRetryableGroups", "countRetryableGroups" }) {
            String sql = configuration.getMappedStatement(FlyingProbeProjectionMapper.class.getName() + "." + statement)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
            assertThat(sql).contains("material_no LIKE CONCAT('%', ?, '%')");
            assertThat(sql).contains("COALESCE(r.work_order_no, p.work_order_no) LIKE CONCAT('%', ?, '%')");
        }
    }
}
