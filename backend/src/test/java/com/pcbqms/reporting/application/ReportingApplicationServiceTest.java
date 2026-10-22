package com.pcbqms.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import com.pcbqms.inspection.infrastructure.persistence.NonconformityMapper;
import com.pcbqms.workorder.infrastructure.persistence.ProductionBatchMapper;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportingApplicationServiceTest {
    @Mock private InspectionRecordMapper inspectionRecordMapper;
    @Mock private InspectionTaskMapper inspectionTaskMapper;
    @Mock private NonconformityMapper nonconformityMapper;
    @Mock private ProductionBatchMapper productionBatchMapper;
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createsTenantScopedQualitySummaryExcel() throws Exception {
        TenantContext.set("customer-a", "tester.a");
        InspectionTask task = new InspectionTask();
        task.setId("task-1");
        task.setTenantCode("customer-a");
        task.setProcessCode("FQC");
        task.setProcessName("成品检验");
        InspectionRecord record = new InspectionRecord();
        record.setTaskId("task-1");
        record.setTenantCode("customer-a");
        record.setVerdict("PASS");
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "SUPERVISOR", true, Set.of()));

        ReportingApplicationService service = new ReportingApplicationService(
            inspectionRecordMapper, inspectionTaskMapper, nonconformityMapper, productionBatchMapper, workOrderMapper,
            inspectionAccessScopeService);

        byte[] content = service.exportQualitySummaryExcel();

        assertThat(content).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheet("质量汇总").getRow(0).getCell(0).getStringCellValue())
                .isEqualTo("PCB 质量汇总报表");
            assertThat(workbook.getSheet("质量汇总").getRow(6).getCell(0).getStringCellValue()).isEqualTo("FQC");
            assertThat(workbook.getSheet("质量汇总").getRow(6).getCell(1).getStringCellValue()).isEqualTo("成品检验");
            assertThat(workbook.getSheet("缺陷排行").getRow(0).getCell(0).getStringCellValue()).isEqualTo("缺陷说明");
            assertThat(workbook.getSheet("质量趋势").getRow(0).getCell(0).getStringCellValue()).isEqualTo("检验日期");
        }
    }

    @Test
    void inspectorSummaryExcludesOtherProcessRecords() {
        TenantContext.set("customer-a", "tester.a");
        InspectionTask fqcTask = task("task-fqc", "FQC", "成品检验");
        InspectionTask maskTask = task("task-mask", "SOLDER_MASK", "阻焊");
        InspectionRecord passRecord = record("task-fqc", "PASS");
        InspectionRecord failRecord = record("task-mask", "FAIL");
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(fqcTask, maskTask));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(passRecord, failRecord));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "INSPECTOR", false, Set.of("FQC")));
        ReportingApplicationService service = new ReportingApplicationService(
            inspectionRecordMapper, inspectionTaskMapper, nonconformityMapper, productionBatchMapper, workOrderMapper,
            inspectionAccessScopeService);

        var summary = service.qualitySummary();

        assertThat(summary.totalRecords()).isEqualTo(1);
        assertThat(summary.passRecords()).isEqualTo(1);
        assertThat(summary.failRecords()).isZero();
        assertThat(summary.byProcess()).extracting(item -> item.processCode()).containsExactly("FQC");
        assertThat(summary.defectRanking()).isEmpty();
    }

    @Test
    void ranksDefectsByQuantityWithinAuthorizedScope() {
        TenantContext.set("customer-a", "supervisor.a");
        InspectionTask fqcTask = task("task-fqc", "FQC", "成品检验");
        InspectionRecord openCircuit = record("task-fqc", "FAIL");
        openCircuit.setDefectSummary("开路");
        openCircuit.setDefectiveQuantity(12);
        InspectionRecord scratch = record("task-fqc", "FAIL");
        scratch.setDefectSummary("划伤");
        scratch.setDefectiveQuantity(3);
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(fqcTask));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(openCircuit, scratch));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("supervisor.a", "SUPERVISOR", true, Set.of()));
        ReportingApplicationService service = new ReportingApplicationService(
            inspectionRecordMapper, inspectionTaskMapper, nonconformityMapper, productionBatchMapper, workOrderMapper,
            inspectionAccessScopeService);

        var summary = service.qualitySummary();

        assertThat(summary.defectRanking()).extracting(metric -> metric.defectSummary())
            .containsExactly("开路", "划伤");
        assertThat(summary.defectRanking().getFirst().defectiveQuantity()).isEqualTo(12);
    }

    @Test
    void filtersSummaryByInclusiveInspectionDateRange() {
        TenantContext.set("customer-a", "supervisor.a");
        InspectionTask fqcTask = task("task-fqc", "FQC", "成品检验");
        InspectionRecord oldRecord = record("task-fqc", "PASS");
        oldRecord.setInspectedAt(LocalDateTime.of(2026, 8, 31, 23, 59));
        InspectionRecord currentRecord = record("task-fqc", "FAIL");
        currentRecord.setInspectedAt(LocalDateTime.of(2026, 9, 1, 8, 30));
        currentRecord.setDefectSummary("孔破");
        currentRecord.setDefectiveQuantity(2);
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(fqcTask));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(oldRecord, currentRecord));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("supervisor.a", "SUPERVISOR", true, Set.of()));
        ReportingApplicationService service = new ReportingApplicationService(
            inspectionRecordMapper, inspectionTaskMapper, nonconformityMapper, productionBatchMapper, workOrderMapper,
            inspectionAccessScopeService);

        var summary = service.qualitySummary(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

        assertThat(summary.totalRecords()).isEqualTo(1);
        assertThat(summary.failRecords()).isEqualTo(1);
        assertThat(summary.defectRanking()).extracting(metric -> metric.defectSummary()).containsExactly("孔破");
        assertThat(summary.dailyTrend()).extracting(metric -> metric.inspectionDate()).containsExactly("2026-09-01");
    }

    @Test
    void filtersSummaryByProcessCode() {
        TenantContext.set("customer-a", "supervisor.a");
        InspectionTask fqcTask = task("task-fqc", "FQC", "成品检验");
        InspectionTask maskTask = task("task-mask", "SOLDER_MASK", "阻焊");
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(fqcTask, maskTask));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(record("task-fqc", "PASS"), record("task-mask", "FAIL")));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("supervisor.a", "SUPERVISOR", true, Set.of()));
        ReportingApplicationService service = new ReportingApplicationService(
            inspectionRecordMapper, inspectionTaskMapper, nonconformityMapper, productionBatchMapper, workOrderMapper,
            inspectionAccessScopeService);

        var summary = service.qualitySummary(null, null, "fqc");

        assertThat(summary.totalRecords()).isEqualTo(1);
        assertThat(summary.passRecords()).isEqualTo(1);
        assertThat(summary.byProcess()).extracting(item -> item.processCode()).containsExactly("FQC");
    }

    @Test
    void fqcProcessFilterExcludesFinishedTasksAtOtherProcesses() {
        TenantContext.set("customer-a", "supervisor.a");
        InspectionTask fqcTask = task("task-fqc", "FQC", "成品检验");
        fqcTask.setTaskType("PROCESS");
        InspectionTask finishedTask = task("task-finished", "ELECTRICAL_TEST", "飞针电测");
        finishedTask.setTaskType("FINISHED");
        InspectionTask maskTask = task("task-mask", "SOLDER_MASK", "阻焊");
        maskTask.setTaskType("PROCESS");
        InspectionRecord failedFinishedRecord = record("task-finished", "FAIL");
        failedFinishedRecord.setDefectSummary("开路");
        failedFinishedRecord.setDefectiveQuantity(2);
        InspectionRecord failedMaskRecord = record("task-mask", "FAIL");
        failedMaskRecord.setDefectSummary("划伤");
        failedMaskRecord.setDefectiveQuantity(3);
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(fqcTask, finishedTask, maskTask));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(
            record("task-fqc", "PASS"), failedFinishedRecord, failedMaskRecord));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("supervisor.a", "SUPERVISOR", true, Set.of()));
        ReportingApplicationService service = new ReportingApplicationService(
            inspectionRecordMapper, inspectionTaskMapper, nonconformityMapper, productionBatchMapper, workOrderMapper,
            inspectionAccessScopeService);

        var summary = service.qualitySummary(null, null, "FQC");

        assertThat(summary.totalRecords()).isEqualTo(1);
        assertThat(summary.passRecords()).isEqualTo(1);
        assertThat(summary.failRecords()).isZero();
        assertThat(summary.byProcess()).extracting(item -> item.processCode())
            .containsExactly("FQC");
        assertThat(summary.defectRanking()).isEmpty();
    }

    @Test
    void finishedTestSummaryIncludesFinishedTasksAndFqcWithinDateRange() {
        TenantContext.set("customer-a", "supervisor.a");
        InspectionTask fqcTask = task("task-fqc", "FQC", "成品检验");
        fqcTask.setTaskType("PROCESS");
        InspectionTask finishedTask = task("task-finished", "ELECTRICAL_TEST", "飞针电测");
        finishedTask.setTaskType("FINISHED");
        InspectionTask maskTask = task("task-mask", "SOLDER_MASK", "阻焊");
        maskTask.setTaskType("PROCESS");
        InspectionRecord fqcRecord = record("task-fqc", "PASS");
        fqcRecord.setInspectedAt(LocalDateTime.of(2026, 9, 1, 8, 30));
        InspectionRecord finishedRecord = record("task-finished", "FAIL");
        finishedRecord.setInspectedAt(LocalDateTime.of(2026, 9, 1, 9, 30));
        finishedRecord.setDefectSummary("开路");
        finishedRecord.setDefectiveQuantity(2);
        InspectionRecord oldFinishedRecord = record("task-finished", "PASS");
        oldFinishedRecord.setInspectedAt(LocalDateTime.of(2026, 8, 31, 23, 59));
        InspectionRecord maskRecord = record("task-mask", "PASS");
        maskRecord.setInspectedAt(LocalDateTime.of(2026, 9, 1, 10, 30));
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(fqcTask, finishedTask, maskTask));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(
            fqcRecord, finishedRecord, oldFinishedRecord, maskRecord));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("supervisor.a", "SUPERVISOR", true, Set.of()));
        ReportingApplicationService service = new ReportingApplicationService(
            inspectionRecordMapper, inspectionTaskMapper, nonconformityMapper, productionBatchMapper, workOrderMapper,
            inspectionAccessScopeService);

        var summary = service.qualitySummary(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null, true);

        assertThat(summary.totalRecords()).isEqualTo(2);
        assertThat(summary.passRecords()).isEqualTo(1);
        assertThat(summary.failRecords()).isEqualTo(1);
        assertThat(summary.byProcess()).extracting(item -> item.processCode())
            .containsExactlyInAnyOrder("FQC", "ELECTRICAL_TEST");
        assertThat(summary.defectRanking()).extracting(item -> item.defectSummary()).containsExactly("开路");
        assertThat(summary.dailyTrend()).extracting(item -> item.inspectionDate()).containsExactly("2026-09-01");
    }

    @Test
    void finishedTestSummaryRespectsInspectorProcessScope() {
        TenantContext.set("customer-a", "tester.a");
        InspectionTask fqcTask = task("task-fqc", "FQC", "成品检验");
        InspectionTask finishedTask = task("task-finished", "ELECTRICAL_TEST", "飞针电测");
        finishedTask.setTaskType("FINISHED");
        when(inspectionTaskMapper.selectList(any())).thenReturn(List.of(fqcTask, finishedTask));
        when(inspectionRecordMapper.selectList(any())).thenReturn(List.of(
            record("task-fqc", "PASS"), record("task-finished", "FAIL")));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "INSPECTOR", false, Set.of("FQC")));
        ReportingApplicationService service = new ReportingApplicationService(
            inspectionRecordMapper, inspectionTaskMapper, nonconformityMapper, productionBatchMapper, workOrderMapper,
            inspectionAccessScopeService);

        var summary = service.qualitySummary(null, null, null, true);

        assertThat(summary.totalRecords()).isEqualTo(1);
        assertThat(summary.byProcess()).extracting(item -> item.processCode()).containsExactly("FQC");
    }

    private InspectionTask task(String id, String processCode, String processName) {
        InspectionTask task = new InspectionTask();
        task.setId(id);
        task.setTenantCode("customer-a");
        task.setProcessCode(processCode);
        task.setProcessName(processName);
        return task;
    }

    private InspectionRecord record(String taskId, String verdict) {
        InspectionRecord record = new InspectionRecord();
        record.setTaskId(taskId);
        record.setTenantCode("customer-a");
        record.setVerdict(verdict);
        return record;
    }
}
