package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

class FinishedTestDeviceReportServiceTest {
    @Test
    void idleSettlementSweepDoesNotHoldOneTransactionAcrossAllMaterials() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var service = new FinishedTestDeviceReportService(jdbcTemplate, mock(InspectionAccessScopeService.class));
        var factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));

        TenantContext.set("customer-b", "test");
        try {
            ((FinishedTestDeviceReportService) factory.getProxy()).settleIdleMaterials(
                LocalDateTime.of(2026, 11, 20, 14, 0));
        } finally {
            TenantContext.clear();
        }

        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void calculatesBoardPassRateWithTwoDecimalPlaces() {
        assertThat(FinishedTestDeviceReportService.passRate(200, 3)).isEqualTo(98.5);
        assertThat(FinishedTestDeviceReportService.passRate(0, 0)).isZero();
    }

    @Test
    void settlesOnlyAfterTwelveHoursAndKeepsDifferentOrdersApart() {
        LocalDateTime first = LocalDateTime.of(2026, 11, 1, 8, 0);
        var samples = List.of(
            new FinishedTestDeviceReportService.Sample("E-1", "PCB-1", "WO-1", first, 5, 1),
            new FinishedTestDeviceReportService.Sample("E-2", "PCB-1", "WO-1", first.plusHours(3), 4, 0));

        assertThat(FinishedTestDeviceReportService.planIdleSettlements(samples, first.plusHours(14))).isEmpty();
        assertThat(FinishedTestDeviceReportService.planIdleSettlements(samples, first.plusHours(15)))
            .singleElement().satisfies(round -> {
                assertThat(round.tested()).isEqualTo(9);
                assertThat(round.bad()).isEqualTo(1);
                assertThat(round.equipmentCount()).isEqualTo(2);
            });
        assertThat(FinishedTestDeviceReportService.planIdleSettlements(List.of(samples.getFirst(),
            new FinishedTestDeviceReportService.Sample("E-2", "PCB-1", "WO-2", first.plusHours(3), 4, 0)),
            first.plusHours(15))).isEmpty();
    }
}
