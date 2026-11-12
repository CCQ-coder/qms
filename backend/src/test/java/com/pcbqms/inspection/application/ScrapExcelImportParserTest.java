package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ScrapExcelImportParserTest {
    private static final String[] HEADERS = {"时间", "层数", "厂内型号", "数量", "单pcs面积", "主要问题",
        "责任部门", "报废总面积", "报废工站", "备注"};

    @Test
    void readsTheProvidedTenColumnLayoutAndKeepsSourceValues() throws Exception {
        byte[] excel = workbook(false, false);

        var result = new ScrapExcelImportParser().parse(excel);

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(1);
        var row = result.rows().getFirst();
        assertThat(row.rowNumber()).isEqualTo(2);
        assertThat(row.date()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(row.internalProductNo()).isEqualTo("31281");
        assertThat(row.layerCount()).isEqualTo(4);
        assertThat(row.quantity()).isEqualTo(55);
        assertThat(row.singlePcsArea()).isEqualByComparingTo("0.02907");
        assertThat(row.scrapArea()).isEqualByComparingTo("1.59885");
        assertThat(row.scrapReason()).isEqualTo("锣坏机器故障");
        assertThat(row.department()).isEqualTo("成型");
        assertThat(row.station()).isEqualTo("来料");
    }

    @Test
    void acceptsTheExportLayoutAndReportsInvalidRows() throws Exception {
        var result = new ScrapExcelImportParser().parse(workbook(true, true));

        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).singleElement().asString().contains("第 3 行", "数量");
    }

    @Test
    void rejectsAnUnrelatedSpreadsheet() throws Exception {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("其他表格");
            workbook.write(bytes);
            assertThatThrownBy(() -> new ScrapExcelImportParser().parse(bytes.toByteArray()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("模板");
        }
    }

    private byte[] workbook(boolean titleRow, boolean invalidQuantity) throws Exception {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            if (titleRow) sheet.createRow(0).createCell(0).setCellValue("报废统计");
            var header = sheet.createRow(titleRow ? 1 : 0);
            for (int i = 0; i < HEADERS.length; i++) header.createCell(i).setCellValue(HEADERS[i]);
            var row = sheet.createRow(titleRow ? 2 : 1);
            row.createCell(0).setCellValue(LocalDate.of(2026, 10, 14).atStartOfDay());
            row.createCell(1).setCellValue(4);
            row.createCell(2).setCellValue(31281);
            row.createCell(3).setCellValue(invalidQuantity ? 0 : 55);
            row.createCell(4).setCellValue(0.02907);
            row.createCell(5).setCellValue("锣坏机器故障");
            row.createCell(6).setCellValue("成型");
            row.createCell(7).setCellValue(1.59885);
            row.createCell(8).setCellValue("来料");
            workbook.write(bytes);
            return bytes.toByteArray();
        }
    }
}
