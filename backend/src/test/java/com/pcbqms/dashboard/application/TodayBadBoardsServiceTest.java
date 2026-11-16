package com.pcbqms.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class TodayBadBoardsServiceTest {
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private InspectionAccessScopeService accessScopeService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void countsOnlyProcessedBadDeltasForTheShanghaiCalendarDay() {
        TenantContext.set("customer-a", "admin");
        LocalDate date = LocalDate.of(2026, 11, 16);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class),
            eq("customer-a"), eq(date.atStartOfDay()), eq(date.plusDays(1).atStartOfDay())))
            .thenReturn(List.of(new TodayBadBoardsService.MaterialBadCount("42946", 9)));

        var result = new TodayBadBoardsService(jdbcTemplate, accessScopeService).forDate(date);

        assertThat(result.date()).isEqualTo("2026-11-16");
        assertThat(result.materials()).singleElement().satisfies(row -> {
            assertThat(row.materialNo()).isEqualTo("42946");
            assertThat(row.badCount()).isEqualTo(9);
        });
        verify(accessScopeService).requireProcessAccess("FQC");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class),
            eq("customer-a"), eq(date.atStartOfDay()), eq(date.plusDays(1).atStartOfDay()));
        assertThat(sql.getValue()).contains("status = 'PROCESSED'", "delta_bad > 0", "SUM(delta_bad)");
    }
}
