package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.integration.domain.SourceDocument;
import com.pcbqms.integration.infrastructure.persistence.SourceDocumentMapper;
import com.pcbqms.integration.infrastructure.storage.LocalAttachmentStorage;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScrapExcelImportWorker {
    private final SourceDocumentMapper documentMapper;
    private final LocalAttachmentStorage storage;
    private final ScrapExcelImportParser parser;
    private final WorkOrderMapper workOrderMapper;
    private final NonconformityApplicationService nonconformityService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${qms.scrap-import.fixed-delay-ms:3000}")
    public synchronized void run() {
        List<SourceDocument> jobs = documentMapper.selectList(Wrappers.lambdaQuery(SourceDocument.class)
            .eq(SourceDocument::getDocumentType, ScrapExcelImportService.DOCUMENT_TYPE)
            .in(SourceDocument::getStatus, "PENDING", "PROCESSING")
            .orderByAsc(SourceDocument::getCreatedAt).last("LIMIT 5"));
        for (SourceDocument document : jobs) {
            if ("PROCESSING".equals(document.getStatus()) && document.getProcessingUpdatedAt() != null
                && document.getProcessingUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) continue;
            TenantContext.set(document.getTenantCode(), "admin");
            try {
                process(document);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void process(SourceDocument document) {
        List<String> errors = new ArrayList<>();
        try {
            saveProgress(document, "PROCESSING", ScrapImportProgress.empty());
            var parsed = parser.parse(Files.readAllBytes(storage.resolve(document.getStoragePath())));
            errors.addAll(parsed.errors().stream().limit(100).toList());
            List<WorkOrder> orders = workOrderMapper.selectList(Wrappers.lambdaQuery(WorkOrder.class)
                .eq(WorkOrder::getTenantCode, document.getTenantCode()));
            int imported = 0;
            int unmatched = 0;
            int invalid = parsed.errors().size();
            int total = parsed.rows().size() + invalid;
            for (var row : parsed.rows()) {
                try {
                    WorkOrder order = ScrapWorkOrderMatcher.closest(row.internalProductNo(), row.date(), orders).orElse(null);
                    nonconformityService.createImportedScrapRow(document.getId(), document.getCreatedBy(), row, order);
                    imported++;
                    if (order == null) unmatched++;
                } catch (RuntimeException exception) {
                    invalid++;
                    if (errors.size() < 100) errors.add("第 " + row.rowNumber() + " 行：写入失败");
                    log.warn("报废明细行导入失败，任务={}，行={}", document.getId(), row.rowNumber(), exception);
                }
                saveProgress(document, "PROCESSING", new ScrapImportProgress(total, imported + invalid,
                    imported, unmatched, invalid, List.copyOf(errors)));
            }
            saveProgress(document, "COMPLETED", new ScrapImportProgress(total, total, imported, unmatched,
                invalid, List.copyOf(errors)));
        } catch (Exception exception) {
            log.warn("报废明细导入任务失败，任务={}", document.getId(), exception);
            errors.add(exception instanceof IllegalArgumentException ? exception.getMessage() : "导入失败，请查看服务日志");
            saveProgress(document, "FAILED", new ScrapImportProgress(0, 0, 0, 0, 0, List.copyOf(errors)));
        }
    }

    private void saveProgress(SourceDocument document, String status, ScrapImportProgress progress) {
        try {
            document.setStatus(status);
            document.setExtractedPayloadJson(objectMapper.writeValueAsString(progress));
            document.setProcessingUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("导入进度无法保存", exception);
        }
    }
}
