package com.pcbqms.dashboard.application;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TodayBadBoardsService {
    private final JdbcTemplate jdbcTemplate;
    private final InspectionAccessScopeService accessScopeService;

    public record MaterialBadCount(String materialNo, long badCount) { }
    public record DailyMaterialBadBoards(String date, List<MaterialBadCount> materials) { }

    public DailyMaterialBadBoards today() {
        return forDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    DailyMaterialBadBoards forDate(LocalDate date) {
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
}
