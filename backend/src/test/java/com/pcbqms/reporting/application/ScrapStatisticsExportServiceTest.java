package com.pcbqms.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcbqms.inspection.api.NonconformityDetailResponse;
import com.pcbqms.inspection.api.NonconformityResponse;
import com.pcbqms.inspection.application.NonconformityApplicationService;
import com.pcbqms.inspection.domain.Nonconformity;
import com.pcbqms.inspection.domain.NonconformityDetail;
import com.pcbqms.workorder.domain.WorkOrder;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScrapStatisticsExportServiceTest {
    @Mock private NonconformityApplicationService nonconformityApplicationService;

    @Test
    void exportsTheRequestedColumnsWithNumericScrapValues() throws Exception {
        NonconformityDetail detail = new NonconformityDetail();
        detail.setProcessName("AOI");
        detail.setScrapQuantity(6);
        detail.setSinglePcsArea(new BigDecimal("0.0276"));
        detail.setScrapArea(new BigDecimal("0.1656"));
        detail.setScrapReason("钻偏");
        detail.setRemark("客户确认");
        WorkOrder order = new WorkOrder();
        order.setWorkOrderNo("WO-001");
        order.setInternalProductNo("029936");
        order.setLayerCount(4);
        NonconformityDetailResponse line = NonconformityDetailResponse.from(detail, order);
        when(nonconformityApplicationService.listNonconformities(null)).thenReturn(List.of(
            ncr("SCRAP", "APPROVED", line), ncr("REWORK", "OPEN", line), ncr("SCRAP", "CANCELLED", line)));

        byte[] content = new ScrapStatisticsExportService(nonconformityApplicationService).exportExcel();

        verify(nonconformityApplicationService).listNonconformities(null);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("报废统计");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("报废统计");
            String[] headers = new String[10];
            for (int index = 0; index < 10; index++) headers[index] = sheet.getRow(1).getCell(index).getStringCellValue();
            assertThat(headers).containsExactly("时间", "层数", "厂内型号", "数量", "单pcs面积", "主要问题",
                "责任部门", "报废总面积", "报废工站", "备注");
            var row = sheet.getRow(2);
            assertThat(row.getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 6, 2));
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(4);
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("029936");
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(6);
            assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(0.0276);
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("钻偏");
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("钻孔");
            assertThat(row.getCell(7).getNumericCellValue()).isEqualTo(0.1656);
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("AOI");
            assertThat(row.getCell(9).getStringCellValue()).isEqualTo("客户确认");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
        }
    }

    private NonconformityResponse ncr(String method, String status, NonconformityDetailResponse detail) {
        Nonconformity ncr = new Nonconformity();
        ncr.setDispositionMethod(method);
        ncr.setStatus(status);
        ncr.setDetectionDate(LocalDate.of(2026, 6, 2));
        ncr.setResponsibleDepartment("钻孔");
        return NonconformityResponse.from(ncr, "WO-001", null, List.of(detail));
    }
}
