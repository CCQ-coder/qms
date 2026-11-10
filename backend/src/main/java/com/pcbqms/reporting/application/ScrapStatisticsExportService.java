package com.pcbqms.reporting.application;

import com.pcbqms.inspection.api.NonconformityDetailResponse;
import com.pcbqms.inspection.application.NonconformityApplicationService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ScrapStatisticsExportService {
    private static final String[] HEADERS = {"时间", "层数", "厂内型号", "数量", "单pcs面积", "主要问题",
        "责任部门", "报废总面积", "报废工站", "备注"};
    private static final int[] COLUMN_WIDTHS = {14, 8, 20, 10, 14, 24, 16, 16, 14, 24};

    private final NonconformityApplicationService nonconformityApplicationService;

    public byte[] exportExcel() {
        var nonconformities = nonconformityApplicationService.listNonconformities(null);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("报废统计");
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            Font titleFont = workbook.createFont();
            titleFont.setFontHeightInPoints((short) 18);
            titleFont.setColor(IndexedColors.BLUE.getIndex());
            titleStyle.setFont(titleFont);

            CellStyle bodyStyle = workbook.createCellStyle();
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderTop(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.THIN);
            bodyStyle.setBorderRight(BorderStyle.THIN);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.cloneStyleFrom(bodyStyle);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.cloneStyleFrom(bodyStyle);
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-m-d"));
            CellStyle areaStyle = workbook.createCellStyle();
            areaStyle.cloneStyleFrom(bodyStyle);
            areaStyle.setDataFormat(workbook.createDataFormat().getFormat("0.######"));

            Row title = sheet.createRow(0);
            title.setHeightInPoints(28);
            title.createCell(0).setCellValue("报废统计");
            title.getCell(0).setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));
            Row header = sheet.createRow(1);
            for (int index = 0; index < HEADERS.length; index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(HEADERS[index]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(index, COLUMN_WIDTHS[index] * 256);
            }

            int rowIndex = 2;
            for (var ncr : nonconformities) {
                if ("CANCELLED".equals(ncr.status()) || (StringUtils.hasText(ncr.dispositionMethod())
                    && !"SCRAP".equals(ncr.dispositionMethod()))) continue;
                for (NonconformityDetailResponse detail : ncr.details()) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int index = 0; index < HEADERS.length; index++) row.createCell(index).setCellStyle(bodyStyle);
                    LocalDate date = ncr.detectionDate() == null && ncr.createdAt() != null
                        ? ncr.createdAt().toLocalDate() : ncr.detectionDate();
                    if (date != null) {
                        row.getCell(0).setCellValue(date.atStartOfDay());
                        row.getCell(0).setCellStyle(dateStyle);
                    }
                    if (detail.layerCount() != null) row.getCell(1).setCellValue(detail.layerCount());
                    row.getCell(2).setCellValue(value(detail.internalProductNo()));
                    if (detail.scrapQuantity() != null) row.getCell(3).setCellValue(detail.scrapQuantity());
                    if (detail.singlePcsArea() != null) row.getCell(4).setCellValue(detail.singlePcsArea().doubleValue());
                    row.getCell(4).setCellStyle(areaStyle);
                    row.getCell(5).setCellValue(value(detail.scrapReason()));
                    row.getCell(6).setCellValue(value(ncr.responsibleDepartment()));
                    if (detail.scrapArea() != null) row.getCell(7).setCellValue(detail.scrapArea().doubleValue());
                    row.getCell(7).setCellStyle(areaStyle);
                    row.getCell(8).setCellValue(value(detail.processName()));
                    row.getCell(9).setCellValue(value(StringUtils.hasText(detail.remark()) ? detail.remark() : ncr.remark()));
                }
            }
            sheet.createFreezePane(0, 2);
            sheet.setAutoFilter(new CellRangeAddress(1, Math.max(1, rowIndex - 1), 0, HEADERS.length - 1));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("报废统计 Excel 生成失败", exception);
        }
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
