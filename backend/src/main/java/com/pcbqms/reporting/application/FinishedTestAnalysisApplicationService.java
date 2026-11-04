package com.pcbqms.reporting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.domain.FinishedTestDataWindow;
import com.pcbqms.reporting.api.FinishedTestAnalysisResponse;
import com.pcbqms.reporting.api.FinishedTestGroupMetric;
import com.pcbqms.reporting.domain.FinishedTestFact;
import com.pcbqms.reporting.infrastructure.persistence.FinishedTestAnalysisMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FinishedTestAnalysisApplicationService {
    private final FinishedTestAnalysisMapper mapper;
    private final InspectionAccessScopeService accessScopeService;
    private final ObjectMapper objectMapper;

    public FinishedTestAnalysisResponse analyze(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        LocalDateTime fromTime = from == null || from.isBefore(FinishedTestDataWindow.START.toLocalDate())
            ? FinishedTestDataWindow.START : from.atStartOfDay();
        LocalDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
        Accumulator total = new Accumulator();
        Map<String, Accumulator> byDate = new LinkedHashMap<>();
        Map<String, Accumulator> byWorkOrder = new LinkedHashMap<>();
        Map<String, Accumulator> byMaterial = new LinkedHashMap<>();
        Map<String, Accumulator> byEquipment = new LinkedHashMap<>();
        if (!accessScopeService.currentAccess().canAccess("FQC") || (toExclusive != null && !toExclusive.isAfter(fromTime))) {
            return response(total, byDate, byWorkOrder, byMaterial, byEquipment);
        }
        for (FinishedTestFact fact : mapper.selectFacts(TenantContext.tenantCode(), fromTime, toExclusive)) {
            if (fact.getInspectedAt() == null || fact.getInspectedAt().isBefore(fromTime)
                || (toExclusive != null && !fact.getInspectedAt().isBefore(toExclusive))) {
                continue;
            }
            Integer boards = boardCount(fact.getResultsJson());
            Integer bad = fact.getDefectiveQuantity();
            boolean quantified = boards != null && bad != null && bad >= 0 && bad <= boards;
            total.add(quantified ? boards : null, quantified ? bad : null);
            add(byDate, fact.getInspectedAt().toLocalDate().toString(), boards, bad, quantified);
            add(byWorkOrder, label(fact.getWorkOrderNo(), "未关联生产指令"), boards, bad, quantified);
            add(byMaterial, label(fact.getMaterialNo(), "未关联料号"), boards, bad, quantified);
            add(byEquipment, label(fact.getEquipmentAssetNo(), "未关联设备"), boards, bad, quantified);
        }
        return response(total, byDate, byWorkOrder, byMaterial, byEquipment);
    }

    public byte[] exportExcel(LocalDate from, LocalDate to) {
        FinishedTestAnalysisResponse analysis = analyze(from, to);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeSheet(workbook, "日期", "检测日期", analysis.byDate());
            writeSheet(workbook, "生产指令", "生产指令", analysis.byWorkOrder());
            writeSheet(workbook, "料号", "料号", analysis.byMaterial());
            writeSheet(workbook, "设备", "设备", analysis.byEquipment());
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("成品测试分析 Excel 生成失败", exception);
        }
    }

    private void writeSheet(Workbook workbook, String name, String keyLabel, List<FinishedTestGroupMetric> metrics) {
        Sheet sheet = workbook.createSheet(name);
        CellStyle percentageStyle = workbook.createCellStyle();
        percentageStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        String[] headers = {keyLabel, "已测板数", "好板", "坏板", "合格率", "记录数", "无数量记录"};
        Row header = sheet.createRow(0);
        for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
        int rowIndex = 1;
        for (FinishedTestGroupMetric metric : metrics) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(metric.key());
            row.createCell(1).setCellValue(metric.totalBoards());
            row.createCell(2).setCellValue(metric.goodBoards());
            row.createCell(3).setCellValue(metric.badBoards());
            var passRate = row.createCell(4);
            passRate.setCellValue(metric.passRate() / 100D);
            passRate.setCellStyle(percentageStyle);
            row.createCell(5).setCellValue(metric.recordCount());
            row.createCell(6).setCellValue(metric.unknownQuantityRecords());
        }
        sheet.setColumnWidth(0, 24 * 256);
        for (int index = 1; index < headers.length; index++) sheet.setColumnWidth(index, 16 * 256);
    }

    private Integer boardCount(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            JsonNode results = objectMapper.readTree(json);
            JsonNode count = results.path("inspectionQuantity");
            if (count.isMissingNode()) count = results.path("testedBoardCount");
            return count.isIntegralNumber() && count.canConvertToInt() && count.intValue() >= 0
                ? count.intValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String label(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private void add(Map<String, Accumulator> groups, String key, Integer boards, Integer bad, boolean quantified) {
        groups.computeIfAbsent(key, ignored -> new Accumulator())
            .add(quantified ? boards : null, quantified ? bad : null);
    }

    private FinishedTestAnalysisResponse response(Accumulator total, Map<String, Accumulator> byDate,
                                                   Map<String, Accumulator> byWorkOrder,
                                                   Map<String, Accumulator> byMaterial,
                                                   Map<String, Accumulator> byEquipment) {
        return new FinishedTestAnalysisResponse(total.recordCount, total.totalBoards, total.goodBoards,
            total.badBoards, total.unknownQuantityRecords, total.passRate(),
            metrics(byDate, true), metrics(byWorkOrder, false), metrics(byMaterial, false), metrics(byEquipment, false));
    }

    private List<FinishedTestGroupMetric> metrics(Map<String, Accumulator> groups, boolean dateOrder) {
        List<FinishedTestGroupMetric> result = new ArrayList<>();
        groups.forEach((key, value) -> result.add(new FinishedTestGroupMetric(key, value.recordCount,
            value.totalBoards, value.goodBoards, value.badBoards, value.unknownQuantityRecords, value.passRate())));
        result.sort(dateOrder ? Comparator.comparing(FinishedTestGroupMetric::key).reversed()
            : Comparator.comparingLong(FinishedTestGroupMetric::totalBoards).reversed()
                .thenComparing(FinishedTestGroupMetric::key));
        return result;
    }

    private static class Accumulator {
        private long recordCount;
        private long totalBoards;
        private long goodBoards;
        private long badBoards;
        private long unknownQuantityRecords;

        void add(Integer boards, Integer bad) {
            recordCount++;
            if (boards == null || bad == null) {
                unknownQuantityRecords++;
            } else {
                totalBoards += boards;
                goodBoards += boards - bad;
                badBoards += bad;
            }
        }

        double passRate() {
            return totalBoards == 0 ? 0 : Math.round(goodBoards * 10000D / totalBoards) / 100D;
        }
    }
}
