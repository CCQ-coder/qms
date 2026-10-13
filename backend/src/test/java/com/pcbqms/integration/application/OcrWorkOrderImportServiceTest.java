package com.pcbqms.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.integration.api.OcrConfirmationRequest;
import com.pcbqms.integration.domain.SourceDocument;
import com.pcbqms.integration.infrastructure.persistence.SourceDocumentMapper;
import com.pcbqms.integration.infrastructure.storage.LocalAttachmentStorage;
import com.pcbqms.workorder.api.WorkOrderResponse;
import com.pcbqms.workorder.application.WorkOrderApplicationService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class OcrWorkOrderImportServiceTest {
    @Mock private LocalAttachmentStorage attachmentStorage;
    @Mock private SourceDocumentMapper sourceDocumentMapper;
    @Mock private WorkOrderApplicationService workOrderApplicationService;
    @Mock private OcrExtractionClient ocrExtractionClient;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void inspectorCanConfirmOwnScreenshotForAssignedProcess() {
        TenantContext.set("customer-a", "tester.a");
        SourceDocument document = screenshot("tester.a");
        when(sourceDocumentMapper.selectById("source-1")).thenReturn(document);
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "INSPECTOR", false, Set.of("FQC")));
        WorkOrderResponse workOrder = new WorkOrderResponse("work-order-1", "WO-001", "PCB-001", "双面板", null,
            "FQC", "成品检验", 100, 0, "OCR", "ACTIVE",
            null, null, null, null, null, null, null, null, null, null, List.of());
        when(workOrderApplicationService.upsertFromOcrConfirmation(any())).thenReturn(workOrder);
        OcrWorkOrderImportService service = service();

        WorkOrderResponse result = service.confirm("source-1", confirmation("FQC"));

        assertThat(result.id()).isEqualTo("work-order-1");
        verify(inspectionAccessScopeService).requireProcessAccess("FQC");
        verify(workOrderApplicationService).upsertFromOcrConfirmation(any());
        ArgumentCaptor<SourceDocument> documentCaptor = ArgumentCaptor.forClass(SourceDocument.class);
        verify(sourceDocumentMapper).updateById(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void inspectorCannotConfirmAnotherUsersScreenshot() {
        TenantContext.set("customer-a", "tester.a");
        when(sourceDocumentMapper.selectById("source-1")).thenReturn(screenshot("tester.b"));
        when(inspectionAccessScopeService.currentAccess()).thenReturn(
            new InspectionAccessScopeService.InspectionAccess("tester.a", "INSPECTOR", false, Set.of("FQC")));

        assertThatThrownBy(() -> service().confirm("source-1", confirmation("FQC")))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(workOrderApplicationService);
    }

    private OcrWorkOrderImportService service() {
        return new OcrWorkOrderImportService(attachmentStorage, sourceDocumentMapper, workOrderApplicationService,
            ocrExtractionClient, new ObjectMapper(), inspectionAccessScopeService);
    }

    private SourceDocument screenshot(String createdBy) {
        SourceDocument document = new SourceDocument();
        document.setId("source-1");
        document.setTenantCode("customer-a");
        document.setDocumentType("ERP_WORK_ORDER_SCREENSHOT");
        document.setStatus("PENDING_REVIEW");
        document.setCreatedBy(createdBy);
        return document;
    }

    private OcrConfirmationRequest confirmation(String processCode) {
        return new OcrConfirmationRequest("WO-001", "PCB-001", "双面板", null, processCode,
            "成品检验", 100, "BATCH-001");
    }
}
