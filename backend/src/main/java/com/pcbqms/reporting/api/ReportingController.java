package com.pcbqms.reporting.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.reporting.application.ReportingApplicationService;
import com.pcbqms.reporting.application.ScrapStatisticsExportService;
import lombok.RequiredArgsConstructor;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportingController {
    private final ReportingApplicationService reportingApplicationService;
    private final ScrapStatisticsExportService scrapStatisticsExportService;

    @GetMapping("/quality-summary")
    public ApiResponse<QualitySummaryResponse> qualitySummary(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String processCode,
        @RequestParam(defaultValue = "false") boolean finishedTest
    ) {
        return ApiResponse.of(reportingApplicationService.qualitySummary(from, to, processCode, finishedTest));
    }

    @GetMapping("/quality-summary.xlsx")
    public ResponseEntity<byte[]> exportQualitySummaryExcel(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        byte[] content = reportingApplicationService.exportQualitySummaryExcel(from, to);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("PCB-质量汇总报表.xlsx", StandardCharsets.UTF_8).build().toString())
            .body(content);
    }

    @GetMapping("/scrap-statistics.xlsx")
    public ResponseEntity<byte[]> exportScrapStatisticsExcel() {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("报废统计.xlsx", StandardCharsets.UTF_8).build().toString())
            .body(scrapStatisticsExportService.exportExcel());
    }

    @GetMapping("/scrap-import-template.xlsx")
    public ResponseEntity<byte[]> scrapImportTemplate() {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("报废明细导入模板.xlsx", StandardCharsets.UTF_8).build().toString())
            .body(scrapStatisticsExportService.templateExcel());
    }

    @GetMapping("/traceability/batches/{batchNo}")
    public ApiResponse<TraceabilityResponse> traceability(@PathVariable String batchNo) {
        return ApiResponse.of(reportingApplicationService.traceByBatch(batchNo));
    }
}
