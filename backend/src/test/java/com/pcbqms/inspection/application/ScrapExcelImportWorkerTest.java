package com.pcbqms.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.integration.domain.SourceDocument;
import com.pcbqms.integration.infrastructure.persistence.SourceDocumentMapper;
import com.pcbqms.integration.infrastructure.storage.LocalAttachmentStorage;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScrapExcelImportWorkerTest {
    @Mock private SourceDocumentMapper documentMapper;
    @Mock private LocalAttachmentStorage storage;
    @Mock private ScrapExcelImportParser parser;
    @Mock private WorkOrderMapper workOrderMapper;
    @Mock private NonconformityApplicationService nonconformityService;
    @TempDir java.nio.file.Path directory;

    @Test
    void processesInBackgroundAndLeavesUnmatchedRowsVisibleForManualBinding() throws Exception {
        var file = directory.resolve("source.xlsx");
        Files.write(file, new byte[] { 1, 2, 3 });
        SourceDocument document = new SourceDocument();
        document.setId("document-1");
        document.setTenantCode("customer-a");
        document.setDocumentType(ScrapExcelImportService.DOCUMENT_TYPE);
        document.setStoragePath("customer-a/source.xlsx");
        document.setStatus("PENDING");
        document.setCreatedBy("importer");
        var row = new ScrapExcelImportParser.ScrapRow(2, LocalDate.of(2026, 10, 14), 4, "31281", 55,
            new BigDecimal("0.02907"), "锣坏", "成型", new BigDecimal("1.59885"), "来料", "");
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(storage.resolve(document.getStoragePath())).thenReturn(file);
        when(parser.parse(any(byte[].class))).thenReturn(new ScrapExcelImportParser.ParseResult(List.of(row), List.of()));
        when(workOrderMapper.selectList(any())).thenReturn(List.of());
        when(nonconformityService.createImportedScrapRow(eq("document-1"), eq("importer"), eq(row), eq(null)))
            .thenReturn(true);

        new ScrapExcelImportWorker(documentMapper, storage, parser, workOrderMapper,
            nonconformityService, new ObjectMapper()).run();

        verify(nonconformityService).createImportedScrapRow("document-1", "importer", row, null);
        assertThat(document.getStatus()).isEqualTo("COMPLETED");
        ScrapImportProgress progress = new ObjectMapper().readValue(document.getExtractedPayloadJson(), ScrapImportProgress.class);
        assertThat(progress.total()).isEqualTo(1);
        assertThat(progress.imported()).isEqualTo(1);
        assertThat(progress.unmatched()).isEqualTo(1);
    }
}
