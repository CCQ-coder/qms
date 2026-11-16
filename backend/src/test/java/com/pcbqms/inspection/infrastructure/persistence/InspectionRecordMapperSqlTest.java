package com.pcbqms.inspection.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class InspectionRecordMapperSqlTest {
    @Test
    void completedRecordsCountMatchingRowsBeforeLimitingFirstPage() {
        String first = sql(0L, true);
        String later = sql(20L, false);

        assertThat(first).contains("COUNT(*) OVER() AS total_count", "LIMIT ? OFFSET ?");
        assertThat(first.indexOf("COUNT(*) OVER()"))
            .isLessThan(first.indexOf("LIMIT ? OFFSET ?"));
        assertThat(later).contains("LIMIT ? OFFSET ?").doesNotContain("COUNT(*) OVER()");
    }

    private String sql(Long offset, boolean includeTotal) {
        Configuration configuration = new Configuration();
        configuration.addMapper(InspectionRecordMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantCode", "customer-a");
        parameters.put("dataStart", LocalDateTime.of(2025, 10, 15, 0, 0));
        parameters.put("processCodes", null);
        parameters.put("cursorTime", null);
        parameters.put("cursorId", null);
        parameters.put("limit", 10);
        parameters.put("offset", offset);
        parameters.put("includeTotal", includeTotal);
        return configuration.getMappedStatement(InspectionRecordMapper.class.getName() + ".selectFinishedPage")
            .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }
}
