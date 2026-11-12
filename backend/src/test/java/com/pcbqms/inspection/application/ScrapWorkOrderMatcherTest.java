package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pcbqms.workorder.domain.WorkOrder;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScrapWorkOrderMatcherTest {
    @Test
    void choosesClosestInputDateNotAfterExcelDate() {
        var excelDate = LocalDate.of(2026, 11, 10);
        var selected = ScrapWorkOrderMatcher.closest("31281", excelDate, List.of(
            order("old", "31281", excelDate.minusDays(10)),
            order("chosen", "02PT31281A01", excelDate.minusDays(1)),
            order("future", "31281", excelDate.plusDays(1)),
            order("other", "131281", excelDate.minusDays(1))));

        assertThat(selected).map(WorkOrder::getWorkOrderNo).contains("chosen");
        assertThat(ScrapWorkOrderMatcher.matches("31281", "131281")).isFalse();
    }

    @Test
    void leavesMissingDateAndSameDayTiesForManualBinding() {
        var date = LocalDate.of(2026, 11, 10);
        assertThat(ScrapWorkOrderMatcher.closest("31281", date, List.of(order("missing", "31281", null)))).isEmpty();
        assertThat(ScrapWorkOrderMatcher.closest("31281", date, List.of(
            order("first", "31281", date), order("second", "31281", date)))).isEmpty();
    }

    private WorkOrder order(String no, String internalNo, LocalDate inputDate) {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo(no);
        order.setInternalProductNo(internalNo);
        order.setInputDate(inputDate);
        return order;
    }
}
