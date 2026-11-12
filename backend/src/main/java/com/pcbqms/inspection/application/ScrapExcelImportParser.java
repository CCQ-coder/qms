package com.pcbqms.inspection.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ScrapExcelImportParser {
    private static final String[] HEADERS = {"时间", "层数", "厂内型号", "数量", "单pcs面积", "主要问题",
        "责任部门", "报废总面积", "报废工站", "备注"};

    public record ScrapRow(int rowNumber, LocalDate date, int layerCount, String internalProductNo,
                           int quantity, BigDecimal singlePcsArea, String scrapReason, String department,
                           BigDecimal scrapArea, String station, String remark) { }
    public record ParseResult(List<ScrapRow> rows, List<String> errors) { }

    public ParseResult parse(byte[] content) {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            int headerIndex = isHeader(sheet.getRow(0)) ? 0 : isHeader(sheet.getRow(1)) ? 1 : -1;
            if (headerIndex < 0) throw new IllegalArgumentException("Excel 模板列名不匹配，请使用报废明细十列模板");
            if (sheet.getLastRowNum() - headerIndex > 10_000) {
                throw new IllegalArgumentException("单个 Excel 最多导入 10000 行");
            }
            List<ScrapRow> rows = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (int index = headerIndex + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                try {
                    if (isBlank(row)) continue;
                    int rowNumber = index + 1;
                    LocalDate date = date(row.getCell(0));
                    int layers = positiveInteger(row.getCell(1), "层数");
                    String internalNo = required(text(row.getCell(2)), "厂内型号", 160);
                    int quantity = positiveInteger(row.getCell(3), "数量");
                    BigDecimal area = nonnegativeDecimal(row.getCell(4), "单pcs面积");
                    String reason = required(text(row.getCell(5)), "主要问题", 500);
                    String department = required(text(row.getCell(6)), "责任部门", 160);
                    BigDecimal totalArea = nonnegativeDecimal(row.getCell(7), "报废总面积");
                    String station = required(text(row.getCell(8)), "报废工站", 160);
                    rows.add(new ScrapRow(rowNumber, date, layers, internalNo, quantity, area, reason,
                        department, totalArea, station, text(row.getCell(9))));
                } catch (IllegalArgumentException exception) {
                    errors.add("第 " + (index + 1) + " 行：" + exception.getMessage());
                }
            }
            return new ParseResult(rows, errors);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("无法读取报废明细 Excel 模板", exception);
        }
    }

    private boolean isHeader(Row row) {
        if (row == null) return false;
        for (int index = 0; index < HEADERS.length; index++) {
            if (!HEADERS[index].equals(text(row.getCell(index)))) return false;
        }
        return true;
    }

    private boolean isBlank(Row row) {
        for (int index = 0; index < HEADERS.length; index++) {
            if (!text(row.getCell(index)).isBlank()) return false;
        }
        return true;
    }

    private LocalDate date(Cell cell) {
        if (cell == null) throw new IllegalArgumentException("时间不能为空");
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
            return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
        }
        try {
            return LocalDate.parse(text(cell).replace('/', '-'));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("时间格式无效", exception);
        }
    }

    private int positiveInteger(Cell cell, String name) {
        try {
            BigDecimal value = new BigDecimal(text(cell));
            int number = value.intValueExact();
            if (number > 0) return number;
        } catch (RuntimeException ignored) { }
        throw new IllegalArgumentException(name + "必须是大于 0 的整数");
    }

    private BigDecimal nonnegativeDecimal(Cell cell, String name) {
        try {
            BigDecimal value = new BigDecimal(text(cell));
            if (value.signum() >= 0 && value.precision() <= 18 && value.scale() <= 6) return value;
        } catch (RuntimeException ignored) { }
        throw new IllegalArgumentException(name + "必须是最多 6 位小数的非负数");
    }

    private String required(String value, String name, int maxLength) {
        if (value.isBlank() || value.length() > maxLength) throw new IllegalArgumentException(name + "不能为空且不得超过 " + maxLength + " 字");
        return value;
    }

    private String text(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BLANK -> "";
            default -> throw new IllegalArgumentException("不支持的单元格格式");
        };
    }
}
