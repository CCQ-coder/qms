package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    void settlesOnlyAfterTwelveHoursWithoutNewBoardsAcrossDevices() {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        order.setInternalProductNo("IN-1");
        LocalDateTime start = LocalDateTime.of(2026, 11, 10, 8, 0);
        var samples = List.of(
            new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "pcb-1", "WO-1", start, 5, 1, true),
            new FinishedTestDeviceSummaryService.Sample("z2", "ZBX", "PCB-1", "WO-1", start.plusHours(2), 5, 1, true),
            new FinishedTestDeviceSummaryService.Sample("p1", "WTD", "PCB-1", "WO-1", start.plusHours(3), 4, 1, false)
        );

        assertThat(FinishedTestDeviceSummaryService.planIdleSettlements(samples, List.of(order),
            start.plusHours(15).minusSeconds(1))).isEmpty();
        assertThat(FinishedTestDeviceSummaryService.planIdleSettlements(samples, List.of(order),
            start.plusHours(15))).singleElement().satisfies(snapshot -> {
                assertThat(snapshot.materialKey()).isEqualTo("PCB-1");
                assertThat(snapshot.workOrderNo()).isEqualTo("WO-1");
                assertThat(snapshot.tested()).isEqualTo(9);
                assertThat(snapshot.good()).isEqualTo(7);
                assertThat(snapshot.bad()).isEqualTo(2);
                assertThat(snapshot.equipmentCount()).isEqualTo(2);
                assertThat(snapshot.lastDetectionAt()).isEqualTo(start.plusHours(3));
            });
    }

    @Test
    void doesNotGuessAWorkOrderWhenOneMaterialHasConflictingBindings() {
        WorkOrder first = new WorkOrder();
        first.setWorkOrderNo("WO-1");
        WorkOrder second = new WorkOrder();
        second.setWorkOrderNo("WO-2");
        LocalDateTime start = LocalDateTime.of(2026, 11, 10, 8, 0);
        var samples = List.of(
            new FinishedTestDeviceSummaryService.Sample("p1", "WTD-1", "PCB-1", "WO-1", start, 4, 0, false),
            new FinishedTestDeviceSummaryService.Sample("p2", "WTD-2", "PCB-1", "WO-2", start.plusHours(1), 3, 1, false)
        );

        assertThat(FinishedTestDeviceSummaryService.planIdleSettlements(samples, List.of(first, second),
            start.plusHours(14))).isEmpty();
    }

    @Test
    void keepsThePreviousIdleRoundWhenADeviceStartsTestingAgain() {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        LocalDateTime first = LocalDateTime.of(2026, 11, 10, 8, 0);
        var samples = List.of(
            new FinishedTestDeviceSummaryService.Sample("p1", "WTD-1", "PCB-1", "WO-1", first, 4, 1, false),
            new FinishedTestDeviceSummaryService.Sample("p2", "WTD-2", "PCB-1", "WO-1",
                first.plusHours(13), 3, 0, false)
        );

        assertThat(FinishedTestDeviceSummaryService.planIdleSettlements(samples, List.of(order),
            first.plusHours(14))).singleElement().satisfies(snapshot -> {
                assertThat(snapshot.firstDetectionAt()).isEqualTo(first);
                assertThat(snapshot.lastDetectionAt()).isEqualTo(first);
                assertThat(snapshot.tested()).isEqualTo(4);
            });
    }

    @Test
    void oneBoundDeviceIdentifiesTheOrderForOtherDevicesWithTheSameMaterial() {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        LocalDateTime first = LocalDateTime.of(2026, 11, 10, 8, 0);
        var samples = List.of(
            new FinishedTestDeviceSummaryService.Sample("p1", "WTD-1", "PCB-1", "WO-1", first, 4, 1, false),
            new FinishedTestDeviceSummaryService.Sample("z1", "ZBX-1", "PCB-1", null,
                first.plusHours(1), 5, 2, true)
        );

        assertThat(FinishedTestDeviceSummaryService.planIdleSettlements(samples, List.of(order),
            first.plusHours(13))).singleElement().satisfies(snapshot -> {
                assertThat(snapshot.workOrderNo()).isEqualTo("WO-1");
                assertThat(snapshot.tested()).isEqualTo(9);
                assertThat(snapshot.bad()).isEqualTo(3);
            });
    }

    @Test
    void unresolvedFlyingProbeDataPreventsAutomaticSettlement() {
        TenantContext.set("tenant-a", "admin");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkOrderMapper orders = mock(WorkOrderMapper.class);
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        LocalDateTime detected = LocalDateTime.of(2026, 11, 10, 8, 0);
        var sample = new FinishedTestDeviceSummaryService.Sample("p1", "WTD-1", "PCB-1", "WO-1",
            detected, 4, 1, false);
        when(orders.selectList(any())).thenReturn(List.of(order));
        when(jdbc.query(any(String.class), org.mockito.ArgumentMatchers.<RowMapper<FinishedTestDeviceSummaryService.Sample>>any(),
            eq("tenant-a"))).thenReturn(List.of(sample), List.of());
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq("tenant-a"), eq("PCB-1"),
            eq(detected), eq(detected.plusHours(12)))).thenReturn(1);

        new FinishedTestDeviceSummaryService(jdbc, orders, mock(InspectionAccessScopeService.class))
            .settleIdleMaterials(detected.plusHours(12));

        verify(jdbc, never()).update(contains("qms_finished_test_settlement"), any(Object[].class));
    }

    @Test
    void laterUnresolvedDataDoesNotBlockAnEarlierClosedRound() {
        TenantContext.set("tenant-a", "admin");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkOrderMapper orders = mock(WorkOrderMapper.class);
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        LocalDateTime first = LocalDateTime.of(2026, 11, 10, 8, 0);
        var sample = new FinishedTestDeviceSummaryService.Sample("p1", "WTD-1", "PCB-1", "WO-1",
            first, 4, 1, false);
        when(orders.selectList(any())).thenReturn(List.of(order));
        when(jdbc.query(any(String.class), org.mockito.ArgumentMatchers.<RowMapper<FinishedTestDeviceSummaryService.Sample>>any(),
            eq("tenant-a"))).thenReturn(List.of(sample), List.of());
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq("tenant-a"), eq("PCB-1"),
            eq(first))).thenReturn(1);
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq("tenant-a"), eq("PCB-1"),
            eq(first), eq(first.plusHours(12)))).thenReturn(0);

        new FinishedTestDeviceSummaryService(jdbc, orders, mock(InspectionAccessScopeService.class))
            .settleIdleMaterials(first.plusHours(24));

        verify(jdbc).update(contains("qms_finished_test_settlement"), any(Object[].class));
    }

    @Test
    void settledRoundLinksUnboundZhongboSamplesToTheOnlyKnownOrder() {
        TenantContext.set("tenant-a", "admin");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkOrderMapper orders = mock(WorkOrderMapper.class);
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        LocalDateTime first = LocalDateTime.of(2026, 11, 10, 8, 0);
        var probe = new FinishedTestDeviceSummaryService.Sample("p1", "WTD-1", "PCB-1", "WO-1",
            first, 4, 1, false);
        var zhongbo = new FinishedTestDeviceSummaryService.Sample("z1", "ZBX-1", "PCB-1", null,
            first.plusHours(1), 5, 2, true);
        when(orders.selectList(any())).thenReturn(List.of(order));
        when(jdbc.query(any(String.class), org.mockito.ArgumentMatchers.<RowMapper<FinishedTestDeviceSummaryService.Sample>>any(),
            eq("tenant-a"))).thenReturn(List.of(probe), List.of(zhongbo));
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq("tenant-a"), eq("PCB-1"),
            eq(first))).thenReturn(0);

        new FinishedTestDeviceSummaryService(jdbc, orders, mock(InspectionAccessScopeService.class))
            .settleIdleMaterials(first.plusHours(13));

        verify(jdbc).update(contains("UPDATE qms_equipment_telemetry"), eq("WO-1"), eq("tenant-a"),
            eq("PCB-1"), eq(first), eq(first.plusHours(13)));
    }

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
    void deviceSummaryKeepsEquipmentAndMaterialDistinctWithoutChangingOverallCounts() {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-1");
        order.setInternalProductNo("IN-1");
        LocalDateTime day = LocalDateTime.of(2026, 11, 3, 9, 0);
        var samples = List.of(
            new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "PCB-1", "WO-1", day, 5, 2, true),
            new FinishedTestDeviceSummaryService.Sample("z2", "ZBX", "PCB-1", "WO-1", day.plusMinutes(5), 8, 3, true),
            new FinishedTestDeviceSummaryService.Sample("z3", "ZBX", "PCB-2", "WO-1", day.plusMinutes(6), 2, 0, false),
            new FinishedTestDeviceSummaryService.Sample("p1", "WTD", "PCB-1", "WO-1", day, 4, 1, false)
        );

        var result = FinishedTestDeviceSummaryService.aggregateByDevice(samples, List.of(order),
            Map.of("ZBX", "ZBX-1", "WTD", "WTD-1"));

        assertThat(result).hasSize(3);
        assertThat(result).filteredOn(row -> row.equipmentCode().equals("ZBX-1") && row.pcbMaterialNo().equals("PCB-1"))
            .singleElement().satisfies(row -> {
                assertThat(row.totalCount()).isEqualTo(8);
                assertThat(row.goodCount()).isEqualTo(5);
                assertThat(row.badCount()).isEqualTo(3);
                assertThat(row.internalProductNo()).isEqualTo("IN-1");
            });
        assertThat(result.stream().mapToLong(FinishedTestDeviceSummaryService.DeviceSummary::totalCount).sum())
            .isEqualTo(FinishedTestDeviceSummaryService.aggregate(samples, List.of(order)).getFirst().totalCount());
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
    void zhongboPcbMaterialMatchesPartOfInternalProductNumber() {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("26100731");
        order.setInternalProductNo("02PT35048A01");
        order.setReleasedAt(LocalDate.of(2026, 10, 27));
        var sample = new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "35048", null,
            LocalDateTime.of(2026, 11, 11, 16, 1), 1, 0, true);

        assertThat(FinishedTestDeviceSummaryService.aggregate(List.of(sample), List.of(order)))
            .singleElement().satisfies(row -> assertThat(row.workOrderNo()).isEqualTo("26100731"));
    }

    @Test
    void zhongboPcbMaterialWithSuffixMatchesByLeadingFiveDigits() {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("26100891");
        order.setInternalProductNo("02PC14814C01");
        order.setReleasedAt(LocalDate.of(2026, 10, 31));
        var sample = new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "14814C+", null,
            LocalDateTime.of(2026, 11, 13, 6, 34), 138, 2, true);

        assertThat(FinishedTestDeviceSummaryService.aggregate(List.of(sample), List.of(order)))
            .singleElement().satisfies(row -> {
                assertThat(row.workOrderNo()).isEqualTo("26100891");
                assertThat(row.pcbMaterialNo()).isEqualTo("14814C+");
            });
    }

    @Test
    void fuzzyZhongboMatchStillSkipsOrdersWithTheSameLatestReleaseDate() {
        WorkOrder first = new WorkOrder();
        first.setWorkOrderNo("26110009");
        first.setInternalProductNo("02PT44382A01");
        first.setReleasedAt(LocalDate.of(2026, 11, 2));
        WorkOrder second = new WorkOrder();
        second.setWorkOrderNo("26110010");
        second.setInternalProductNo("02PT44382A01");
        second.setReleasedAt(LocalDate.of(2026, 11, 2));
        var sample = new FinishedTestDeviceSummaryService.Sample("z1", "ZBX", "44382", null,
            LocalDateTime.of(2026, 11, 10, 19, 36), 1, 0, true);

        assertThat(FinishedTestDeviceSummaryService.aggregate(List.of(sample), List.of(first, second))).isEmpty();
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
    void cancelZhongboBindingSkipsAnExistingDeviceDisposition() {
        TenantContext.set("customer-a", "supervisor");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), org.mockito.ArgumentMatchers.<RowMapper<FinishedTestDeviceSummaryService.Binding>>any(),
            eq("z1"), eq("customer-a"))).thenReturn(List.of(
                new FinishedTestDeviceSummaryService.Binding("WO-1", LocalDate.of(2026, 11, 3))));
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq("customer-a"), eq("customer-a"),
            eq("WO-1"), eq(LocalDate.of(2026, 11, 3)))).thenReturn(1);

        var service = new FinishedTestDeviceSummaryService(jdbc, mock(WorkOrderMapper.class),
            mock(InspectionAccessScopeService.class));

        assertThatThrownBy(() -> service.unbindZhongboWorkOrder("z1"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("处置单");
        verify(jdbc, never()).update(any(String.class), any(), any());
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
