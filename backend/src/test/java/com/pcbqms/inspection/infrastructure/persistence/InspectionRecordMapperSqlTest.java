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

    @Test
    void completedRecordsFilterByDeviceAndOrderFieldsBeforeCountingAndPaging() {
        String filtered = sql(0L, true, "WTD-3", "26110533", "L2611110293", "P2H046615A");

        assertThat(filtered).contains(
            "record.equipment_asset_no = ?",
            "work_order.work_order_no LIKE CONCAT('%', ?, '%')",
            "work_order.customer_order_no LIKE CONCAT('%', ?, '%')",
            "work_order.internal_product_no LIKE CONCAT('%', ?, '%')",
            "COUNT(*) OVER() AS total_count",
            "LIMIT ? OFFSET ?"
        );
        assertThat(filtered.indexOf("work_order.internal_product_no LIKE"))
            .isLessThan(filtered.indexOf("LIMIT ? OFFSET ?"));
        assertThat(sql(0L, true)).doesNotContain("work_order.work_order_no LIKE", "record.equipment_asset_no = ?");
    }

    private String sql(Long offset, boolean includeTotal) {
        return sql(offset, includeTotal, null, null, null, null);
    }

    private String sql(Long offset, boolean includeTotal, String equipmentAssetNo, String workOrderNo,
                       String customerOrderNo, String internalProductNo) {
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
        parameters.put("equipmentAssetNo", equipmentAssetNo);
        parameters.put("workOrderNo", workOrderNo);
        parameters.put("customerOrderNo", customerOrderNo);
        parameters.put("internalProductNo", internalProductNo);
        return configuration.getMappedStatement(InspectionRecordMapper.class.getName() + ".selectFinishedPage")
            .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }
}
