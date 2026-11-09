package com.pcbqms.inspection.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class FlyingProbeProjectionMapperSqlTest {
    @Test
    void retryableListAndCountUseTheSameSearchConditions() {
        Configuration configuration = new Configuration();
        configuration.addMapper(FlyingProbeProjectionMapper.class);
        Map<String, Object> parameters = Map.of(
            "tenantCode", "customer-b", "workOrderNo", "WO-1", "internalProductNo", "02PT42777A01",
            "pageSize", 50, "offset", 0);

        for (String method : new String[] {"selectRetryableGroups", "countRetryableGroups"}) {
            String sql = configuration.getMappedStatement(FlyingProbeProjectionMapper.class.getName() + "." + method)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
            assertThat(sql).contains("p.material_no LIKE CONCAT('%', ?, '%')",
                "COALESCE(r.work_order_no, p.work_order_no) LIKE CONCAT('%', ?, '%')");
        }
    }
}
