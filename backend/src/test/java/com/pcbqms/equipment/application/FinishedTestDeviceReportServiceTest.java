package com.pcbqms.equipment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinishedTestDeviceReportServiceTest {
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
