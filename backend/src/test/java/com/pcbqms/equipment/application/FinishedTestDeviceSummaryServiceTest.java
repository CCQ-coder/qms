package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import com.pcbqms.workorder.domain.WorkOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

class FinishedTestDeviceSummaryServiceTest {
    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void countsCounterDeltasOnceAndGroupsBothDevicesByDateAndProductionOrder() {
        WorkOrder order = new WorkOrder();
        order.setId("order-1");
        order.setWorkOrderNo("WO-1");
        order.setInternalProductNo("42379");
        order.setReleasedAt(LocalDate.of(2026, 11, 1));
        LocalDateTime morning = LocalDateTime.of(2026, 11, 3, 9, 0);
        var samples = List.of(
            new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "42379", null, morning, 5, 2, true),
            new FinishedTestDeviceSummaryService.Sample("z2", "ZBX", "42379", null, morning.plusMinutes(5), 8, 3, true),
            new FinishedTestDeviceSummaryService.Sample("p1", "WTD", "42379", "WO-1", morning, 4, 1, false),
            new FinishedTestDeviceSummaryService.Sample("unmatched", "ZBX", "other", null, morning, 20, 10, true)
        );

        var result = FinishedTestDeviceSummaryService.aggregate(samples, List.of(order));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().totalCount()).isEqualTo(12);
        assertThat(result.getFirst().goodCount()).isEqualTo(8);
        assertThat(result.getFirst().badCount()).isEqualTo(4);
        assertThat(result.getFirst().workOrderNo()).isEqualTo("WO-1");
        assertThat(result.getFirst().inspectionDate()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    void ambiguousLatestProductionOrderIsNotGuessed() {
        WorkOrder first = new WorkOrder();
        first.setWorkOrderNo("WO-1");
        first.setInternalProductNo("42379");
        first.setReleasedAt(LocalDate.of(2026, 11, 1));
        WorkOrder second = new WorkOrder();
        second.setWorkOrderNo("WO-2");
        second.setInternalProductNo("42379");
        second.setReleasedAt(LocalDate.of(2026, 11, 1));
        var sample = new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "42379", null,
            LocalDateTime.of(2026, 11, 3, 9, 0), 5, 2, true);

        assertThat(FinishedTestDeviceSummaryService.aggregate(List.of(sample), List.of(first, second))).isEmpty();
    }

    @Test
    void zhongboPcbMaterialCanMatchProductionOrderMaterialNumber() {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        order.setInternalProductNo("IN-1");
        order.setMaterialNo("PCB-42379");
        order.setReleasedAt(LocalDate.of(2026, 11, 1));
        var sample = new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "PCB-42379", null,
            LocalDateTime.of(2026, 11, 3, 9, 0), 5, 2, true);

        assertThat(FinishedTestDeviceSummaryService.aggregate(List.of(sample), List.of(order)))
            .singleElement().satisfies(row -> assertThat(row.internalProductNo()).isEqualTo("IN-1"));
    }

    @Test
    void currentMonthAnalyticsUsesDeviceDeltasAndRanksInternalNumbers() {
        WorkOrder first = new WorkOrder();
        first.setWorkOrderNo("WO-1");
        first.setInternalProductNo("IN-1");
        WorkOrder second = new WorkOrder();
        second.setWorkOrderNo("WO-2");
        second.setInternalProductNo("IN-2");
        LocalDateTime day = LocalDateTime.of(2026, 11, 3, 9, 0);
        var samples = List.of(
            new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "PCB-1", "WO-1", day, 5, 2, true),
            new FinishedTestDeviceSummaryService.Sample("z2", "ZBX", "PCB-1", "WO-1", day.plusMinutes(5), 8, 3, true),
            new FinishedTestDeviceSummaryService.Sample("p1", "WTD", "PCB-2", "WO-2", day, 4, 2, false),
            new FinishedTestDeviceSummaryService.Sample("old", "WTD", "PCB-2", "WO-2", day.minusMonths(1), 10, 9, false)
        );

        var result = FinishedTestDeviceSummaryService.aggregateMonthly(samples, List.of(first, second),
            Map.of("ZBX", "众博信 X", "WTD", "旺通达 Y"), YearMonth.of(2026, 11));

        assertThat(result.month()).isEqualTo("2026-11");
        assertThat(result.byModel()).extracting(item -> item.totalCount()).containsExactly(8L, 4L);
        assertThat(result.byModel()).extracting(item -> item.badCount()).containsExactly(3L, 2L);
        assertThat(result.topInternalDefects()).extracting(item -> item.internalProductNo())
            .containsExactly("IN-1", "IN-2");
    }

    @Test
    void monthlyModelIncludesUnboundZhongboWithoutAssigningItsDefectsToAnInternalNumber() {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        order.setInternalProductNo("IN-1");
        LocalDateTime day = LocalDateTime.of(2026, 11, 10, 9, 0);
        var samples = List.of(
            new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "PCB-1", null, day, 3, 1, true),
            new FinishedTestDeviceSummaryService.Sample("z2", "ZBX", "PCB-1", null, day.plusMinutes(5), 5, 2, true),
            new FinishedTestDeviceSummaryService.Sample("p1", "WTD", "PCB-2", "WO-1", day, 4, 1, false)
        );

        var result = FinishedTestDeviceSummaryService.aggregateMonthly(samples, List.of(order),
            Map.of("ZBX", "众博信", "WTD", "旺通达"), YearMonth.of(2026, 11));

        assertThat(result.byModel()).extracting(item -> item.model() + ":" + item.totalCount() + ":" + item.badCount())
            .containsExactly("众博信:5:2", "旺通达:4:1");
        assertThat(result.topInternalDefects()).extracting(item -> item.internalProductNo() + ":" + item.badCount())
            .containsExactly("IN-1:1");
    }

    @Test
    void reviewedDeviceCountsCannotBeMovedToAnotherProductionOrder() {
        TenantContext.set("customer-a", "supervisor");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkOrderMapper orders = mock(WorkOrderMapper.class);
        WorkOrder target = new WorkOrder();
        target.setWorkOrderNo("WO-2");
        when(orders.selectOne(any())).thenReturn(target);
        when(jdbc.query(any(String.class), org.mockito.ArgumentMatchers.<RowMapper<FinishedTestDeviceSummaryService.Binding>>any(),
            eq("z1"), eq("customer-a"))).thenReturn(List.of(
                new FinishedTestDeviceSummaryService.Binding("WO-1", LocalDate.of(2026, 11, 3))));
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq("customer-a"), eq("customer-a"),
            eq("WO-1"), eq(LocalDate.of(2026, 11, 3)))).thenReturn(1);

        var service = new FinishedTestDeviceSummaryService(jdbc, orders, mock(InspectionAccessScopeService.class));

        assertThatThrownBy(() -> service.bindZhongboWorkOrder("z1", "WO-2"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已审核");
    }

    @Test
    void scheduledSummaryDoesNotNeedAProvisionedAdminButApiSummaryStillChecksAccess() {
        TenantContext.set("customer-a", "admin");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkOrderMapper orders = mock(WorkOrderMapper.class);
        InspectionAccessScopeService access = mock(InspectionAccessScopeService.class);
        when(jdbc.query(any(String.class), org.mockito.ArgumentMatchers.<RowMapper<FinishedTestDeviceSummaryService.Sample>>any(),
            eq("customer-a"))).thenReturn(List.of());
        when(orders.selectList(any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new AccessDeniedException("not provisioned"))
            .when(access).requireProcessAccess("FQC");
        var service = new FinishedTestDeviceSummaryService(jdbc, orders, access);

        assertThat(service.summariesForCurrentTenant()).isEmpty();
        assertThatThrownBy(service::listSummary).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void dailyBadBoardsUsesDetectionDayTenantAndProcessedBoardDeltas() {
        TenantContext.set("customer-a", "admin");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InspectionAccessScopeService access = mock(InspectionAccessScopeService.class);
        LocalDate day = LocalDate.of(2026, 11, 10);
        when(jdbc.query(any(String.class),
            org.mockito.ArgumentMatchers.<RowMapper<FinishedTestDeviceSummaryService.MaterialBadCount>>any(),
            eq("customer-a"), eq(day.atStartOfDay()), eq(day.plusDays(1).atStartOfDay())))
            .thenReturn(List.of(new FinishedTestDeviceSummaryService.MaterialBadCount("44496", 3)));

        var result = new FinishedTestDeviceSummaryService(jdbc, mock(WorkOrderMapper.class), access)
            .badBoardsForDate(day);

        assertThat(result.date()).isEqualTo("2026-11-10");
        assertThat(result.materials()).containsExactly(
            new FinishedTestDeviceSummaryService.MaterialBadCount("44496", 3));
        verify(access).requireProcessAccess("FQC");
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(),
            org.mockito.ArgumentMatchers.<RowMapper<FinishedTestDeviceSummaryService.MaterialBadCount>>any(),
            eq("customer-a"), eq(day.atStartOfDay()), eq(day.plusDays(1).atStartOfDay()));
        assertThat(sql.getValue()).contains("status = 'PROCESSED'", "delta_bad > 0", "GROUP BY", "ORDER BY");
    }
}
