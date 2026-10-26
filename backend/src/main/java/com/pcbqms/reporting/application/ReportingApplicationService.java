package com.pcbqms.reporting.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.domain.Nonconformity;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.NonconformityMapper;
import com.pcbqms.reporting.api.DefectRankingMetric;
import com.pcbqms.reporting.api.ProcessQualityMetric;
import com.pcbqms.reporting.api.QualitySummaryResponse;
import com.pcbqms.reporting.api.QualityTrendMetric;
import com.pcbqms.reporting.api.TraceabilityResponse;
import com.pcbqms.workorder.api.WorkOrderResponse;
import com.pcbqms.workorder.domain.ProductionBatch;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ReportingApplicationService {
    private final InspectionRecordMapper inspectionRecordMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final NonconformityMapper nonconformityMapper;
    private final ProductionBatchMapper productionBatchMapper;
    private final WorkOrderMapper workOrderMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public QualitySummaryResponse qualitySummary() {
        return qualitySummary(null, null);
    }

    /** Aggregates submitted inspections in an optional inclusive inspection-date range. */
    public QualitySummaryResponse qualitySummary(LocalDate from, LocalDate to) {
        return qualitySummary(from, to, null);
    }

    /** Aggregates submitted inspections in an optional inclusive inspection-date and process range. */
    public QualitySummaryResponse qualitySummary(LocalDate from, LocalDate to, String processCode) {
        return qualitySummary(from, to, processCode, false);
    }

    /** Optionally limits the summary to finished-test tasks and FQC process tasks. */
    public QualitySummaryResponse qualitySummary(LocalDate from, LocalDate to, String processCode, boolean finishedTest) {
        assertValidDateRange(from, to);
        String normalizedProcessCode = StringUtils.hasText(processCode) ? processCode.trim() : null;
        String tenantCode = TenantContext.tenantCode();
        var access = inspectionAccessScopeService.currentAccess();
        List<InspectionRecord> records = inspectionRecordMapper.selectList(Wrappers.lambdaQuery(InspectionRecord.class)
            .eq(InspectionRecord::getTenantCode, tenantCode));
        Map<String, InspectionTask> taskById = new LinkedHashMap<>();
        for (InspectionTask task : inspectionTaskMapper.selectList(Wrappers.lambdaQuery(InspectionTask.class)
            .eq(InspectionTask::getTenantCode, tenantCode))) {
            if (access.fullDataAccess() || access.canAccess(task.getProcessCode())) {
                taskById.put(task.getId(), task);
            }
        }
        Map<String, long[]> counters = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, long[]> defectCounters = new LinkedHashMap<>();
        Map<LocalDate, long[]> trendCounters = new TreeMap<LocalDate, long[]>(
            finishedTest ? Comparator.reverseOrder() : Comparator.naturalOrder());
        long passCount = 0;
        long totalCount = 0;
        for (InspectionRecord record : records) {
            if (!isInDateRange(record, from, to)) {
                continue;
            }
            InspectionTask task = taskById.get(record.getTaskId());
            if (!access.fullDataAccess() && task == null) {
                continue;
            }
            if (normalizedProcessCode != null && (task == null || !normalizedProcessCode.equalsIgnoreCase(task.getProcessCode()))) {
                continue;
            }
            if (finishedTest && !matchesFinishedTest(task)) {
                continue;
            }
            totalCount++;
            String taskProcessCode = task == null ? "UNKNOWN" : task.getProcessCode();
            names.put(taskProcessCode, task == null ? "未匹配工序" : task.getProcessName());
            long[] counter = counters.computeIfAbsent(taskProcessCode, ignored -> new long[2]);
            counter[0]++;
            LocalDate inspectionDate = record.getInspectedAt() == null ? null : record.getInspectedAt().toLocalDate();
            long[] trendCounter = inspectionDate == null ? null : trendCounters.computeIfAbsent(inspectionDate, ignored -> new long[2]);
            if (trendCounter != null) {
                trendCounter[0]++;
            }
            if ("PASS".equals(record.getVerdict())) {
                counter[1]++;
                passCount++;
                if (trendCounter != null) {
                    trendCounter[1]++;
                }
            } else {
                String defectSummary = StringUtils.hasText(record.getDefectSummary())
                    ? record.getDefectSummary().trim() : "未填写缺陷说明";
                long[] defectCounter = defectCounters.computeIfAbsent(defectSummary, ignored -> new long[2]);
                defectCounter[0]++;
                defectCounter[1] += Math.max(record.getDefectiveQuantity() == null ? 0 : record.getDefectiveQuantity(), 0);
            }
        }
        List<ProcessQualityMetric> byProcess = counters.entrySet().stream()
            .map(entry -> {
                long total = entry.getValue()[0];
                long pass = entry.getValue()[1];
                return new ProcessQualityMetric(entry.getKey(), names.get(entry.getKey()), total, pass, total - pass,
                    total == 0 ? 0D : Math.round(pass * 10000D / total) / 100D);
            }).toList();
        List<DefectRankingMetric> defectRanking = defectCounters.entrySet().stream()
            .map(entry -> new DefectRankingMetric(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
            .sorted((left, right) -> {
                int quantityOrder = Long.compare(right.defectiveQuantity(), left.defectiveQuantity());
                if (quantityOrder != 0) {
                    return quantityOrder;
                }
                int recordOrder = Long.compare(right.failedRecords(), left.failedRecords());
                return recordOrder != 0 ? recordOrder : left.defectSummary().compareTo(right.defectSummary());
            })
            .limit(10)
            .toList();
        List<QualityTrendMetric> dailyTrend = trendCounters.entrySet().stream()
            .map(entry -> {
                long total = entry.getValue()[0];
                long pass = entry.getValue()[1];
                return new QualityTrendMetric(entry.getKey().toString(), total, pass, total - pass,
                    total == 0 ? 0D : Math.round(pass * 10000D / total) / 100D);
            })
            .toList();
        long total = totalCount;
        return new QualitySummaryResponse(total, passCount, total - passCount,
            total == 0 ? 0D : Math.round(passCount * 10000D / total) / 100D, byProcess, defectRanking, dailyTrend);
    }

    /** Exports the same tenant-scoped data shown on the quality report page. */
    public byte[] exportQualitySummaryExcel() {
        return exportQualitySummaryExcel(null, null);
    }

    /** Exports exactly the date range and authorization scope shown on the quality report page. */
    public byte[] exportQualitySummaryExcel(LocalDate from, LocalDate to) {
        QualitySummaryResponse summary = qualitySummary(from, to);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("质量汇总");
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 15);
            titleStyle.setFont(titleFont);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle percentageStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            percentageStyle.setDataFormat(dataFormat.getFormat("0.00%"));

            Row title = sheet.createRow(0);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("PCB 质量汇总报表");
            titleCell.setCellStyle(titleStyle);
            sheet.createRow(1).createCell(0).setCellValue("检验日期：" + dateRangeLabel(from, to));

            String[] summaryLabels = {"检验记录", "合格记录", "不合格记录", "一次合格率"};
            double[] summaryValues = {summary.totalRecords(), summary.passRecords(), summary.failRecords(), summary.firstPassRate() / 100D};
            Row summaryHeader = sheet.createRow(2);
            Row summaryValue = sheet.createRow(3);
            for (int index = 0; index < summaryLabels.length; index++) {
                Cell header = summaryHeader.createCell(index);
                header.setCellValue(summaryLabels[index]);
                header.setCellStyle(headerStyle);
                Cell value = summaryValue.createCell(index);
                value.setCellValue(summaryValues[index]);
                if (index == summaryLabels.length - 1) {
                    value.setCellStyle(percentageStyle);
                }
            }

            String[] processHeaders = {"工序编码", "工序名称", "检验记录数", "合格数", "不合格数", "一次合格率"};
            Row processHeader = sheet.createRow(5);
            for (int index = 0; index < processHeaders.length; index++) {
                Cell cell = processHeader.createCell(index);
                cell.setCellValue(processHeaders[index]);
                cell.setCellStyle(headerStyle);
            }
            int rowIndex = 6;
            for (ProcessQualityMetric metric : summary.byProcess()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(metric.processCode());
                row.createCell(1).setCellValue(metric.processName());
                row.createCell(2).setCellValue(metric.totalRecords());
                row.createCell(3).setCellValue(metric.passRecords());
                row.createCell(4).setCellValue(metric.failRecords());
                Cell passRate = row.createCell(5);
                passRate.setCellValue(metric.passRate() / 100D);
                passRate.setCellStyle(percentageStyle);
            }
            for (int index = 0; index < processHeaders.length; index++) {
                sheet.autoSizeColumn(index);
                sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 768, 24 * 256));
            }

            Sheet defectsSheet = workbook.createSheet("缺陷排行");
            String[] defectHeaders = {"缺陷说明", "不合格记录数", "不良数量"};
            Row defectHeader = defectsSheet.createRow(0);
            for (int index = 0; index < defectHeaders.length; index++) {
                Cell cell = defectHeader.createCell(index);
                cell.setCellValue(defectHeaders[index]);
                cell.setCellStyle(headerStyle);
            }
            int defectRowIndex = 1;
            for (DefectRankingMetric metric : summary.defectRanking()) {
                Row row = defectsSheet.createRow(defectRowIndex++);
                row.createCell(0).setCellValue(metric.defectSummary());
                row.createCell(1).setCellValue(metric.failedRecords());
                row.createCell(2).setCellValue(metric.defectiveQuantity());
            }
            for (int index = 0; index < defectHeaders.length; index++) {
                defectsSheet.autoSizeColumn(index);
                defectsSheet.setColumnWidth(index, Math.min(defectsSheet.getColumnWidth(index) + 768, 36 * 256));
            }

            Sheet trendSheet = workbook.createSheet("质量趋势");
            String[] trendHeaders = {"检验日期", "检验记录数", "合格数", "不合格数", "一次合格率"};
            Row trendHeader = trendSheet.createRow(0);
            for (int index = 0; index < trendHeaders.length; index++) {
                Cell cell = trendHeader.createCell(index);
                cell.setCellValue(trendHeaders[index]);
                cell.setCellStyle(headerStyle);
            }
            int trendRowIndex = 1;
            for (QualityTrendMetric metric : summary.dailyTrend()) {
                Row row = trendSheet.createRow(trendRowIndex++);
                row.createCell(0).setCellValue(metric.inspectionDate());
                row.createCell(1).setCellValue(metric.totalRecords());
                row.createCell(2).setCellValue(metric.passRecords());
                row.createCell(3).setCellValue(metric.failRecords());
                Cell passRate = row.createCell(4);
                passRate.setCellValue(metric.passRate() / 100D);
                passRate.setCellStyle(percentageStyle);
            }
            for (int index = 0; index < trendHeaders.length; index++) {
                trendSheet.autoSizeColumn(index);
                trendSheet.setColumnWidth(index, Math.min(trendSheet.getColumnWidth(index) + 768, 24 * 256));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("质量汇总 Excel 生成失败", exception);
        }
    }

    public TraceabilityResponse traceByBatch(String batchNo) {
        String tenantCode = TenantContext.tenantCode();
        var access = inspectionAccessScopeService.currentAccess();
        ProductionBatch batch = productionBatchMapper.selectOne(Wrappers.lambdaQuery(ProductionBatch.class)
            .eq(ProductionBatch::getTenantCode, tenantCode)
            .eq(ProductionBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new IllegalArgumentException("生产批次不存在：" + batchNo);
        }
        WorkOrder workOrder = workOrderMapper.selectById(batch.getWorkOrderId());
        if (workOrder == null || !tenantCode.equals(workOrder.getTenantCode())) {
            throw new IllegalArgumentException("批次关联的工单不存在");
        }
        List<ProductionBatch> batches = productionBatchMapper.selectList(Wrappers.lambdaQuery(ProductionBatch.class)
            .eq(ProductionBatch::getTenantCode, tenantCode).eq(ProductionBatch::getWorkOrderId, workOrder.getId()));
        List<InspectionTask> tasks = inspectionTaskMapper.selectList(Wrappers.lambdaQuery(InspectionTask.class)
            .eq(InspectionTask::getTenantCode, tenantCode).eq(InspectionTask::getBatchId, batch.getId())
            .orderByAsc(InspectionTask::getCreatedAt)).stream()
            .filter(task -> access.fullDataAccess() || access.canAccess(task.getProcessCode()))
            .toList();
        if (!access.fullDataAccess() && tasks.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException("无权查看该批次的追溯数据");
        }
        Map<String, InspectionTask> tasksById = new LinkedHashMap<>();
        for (InspectionTask task : tasks) {
            tasksById.put(task.getId(), task);
        }
        List<InspectionRecord> records = tasks.isEmpty() ? List.of() : inspectionRecordMapper.selectList(Wrappers.lambdaQuery(InspectionRecord.class)
            .eq(InspectionRecord::getTenantCode, tenantCode).in(InspectionRecord::getTaskId, tasksById.keySet())
            .orderByAsc(InspectionRecord::getInspectedAt));
        List<TraceabilityResponse.TraceInspectionRecord> inspectionRecords = new ArrayList<>();
        for (InspectionRecord record : records) {
            InspectionTask task = tasksById.get(record.getTaskId());
            inspectionRecords.add(new TraceabilityResponse.TraceInspectionRecord(
                task == null ? "已删除任务" : task.getTaskNo(),
                task == null ? "未匹配工序" : task.getProcessName(),
                task == null ? "UNKNOWN" : task.getTaskType(), record.getVerdict(), record.getDefectiveQuantity(),
                record.getDefectSummary(), record.getDefectLocation(), record.getInspectedBy(), record.getInspectedAt()));
        }
        List<TraceabilityResponse.TraceNonconformity> nonconformities = nonconformityMapper.selectList(Wrappers.lambdaQuery(Nonconformity.class)
                .eq(Nonconformity::getTenantCode, tenantCode).eq(Nonconformity::getBatchId, batch.getId())
                .orderByDesc(Nonconformity::getCreatedAt))
            .stream()
            .filter(ncr -> canAccessNonconformity(ncr, tasksById, access.fullDataAccess()))
            .map(ncr -> new TraceabilityResponse.TraceNonconformity(ncr.getNcrNo(), ncr.getStatus(),
                ncr.getInitiatedBy(), ncr.getCreatedAt())).toList();
        return new TraceabilityResponse(WorkOrderResponse.from(workOrder, batches), batch.getBatchNo(), inspectionRecords, nonconformities);
    }

    private boolean canAccessNonconformity(Nonconformity ncr, Map<String, InspectionTask> visibleTasks, boolean fullDataAccess) {
        if (fullDataAccess) {
            return true;
        }
        InspectionRecord record = inspectionRecordMapper.selectById(ncr.getInspectionRecordId());
        return record != null && TenantContext.tenantCode().equals(record.getTenantCode()) && visibleTasks.containsKey(record.getTaskId());
    }

    private void assertValidDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
    }

    private boolean isInDateRange(InspectionRecord record, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (record.getInspectedAt() == null) {
            return false;
        }
        LocalDate inspectedDate = record.getInspectedAt().toLocalDate();
        return (from == null || !inspectedDate.isBefore(from)) && (to == null || !inspectedDate.isAfter(to));
    }

    private boolean matchesFinishedTest(InspectionTask task) {
        return task != null && ("FINISHED".equalsIgnoreCase(task.getTaskType())
            || "FQC".equalsIgnoreCase(task.getProcessCode()));
    }

    private String dateRangeLabel(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return "全部";
        }
        return (from == null ? "最早记录" : from.toString()) + " 至 " + (to == null ? "当前" : to.toString());
    }
}
