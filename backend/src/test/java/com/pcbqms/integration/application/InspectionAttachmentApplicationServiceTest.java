package com.pcbqms.integration.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.integration.domain.SourceDocument;
import com.pcbqms.integration.infrastructure.persistence.SourceDocumentMapper;
import com.pcbqms.integration.infrastructure.storage.LocalAttachmentStorage;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class InspectionAttachmentApplicationServiceTest {
    @Mock private LocalAttachmentStorage attachmentStorage;
    @Mock private SourceDocumentMapper sourceDocumentMapper;
    @Mock private InspectionRecordMapper inspectionRecordMapper;
    @Mock private InspectionTaskMapper inspectionTaskMapper;
    @Mock private InspectionAccessScopeService inspectionAccessScopeService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void inspectorCannotReadPhotoFromAnotherProcess() {
        TenantContext.set("customer-a", "tester.a");
        SourceDocument photo = new SourceDocument();
        photo.setTenantCode("customer-a");
        photo.setDocumentType("INSPECTION_PHOTO");
        photo.setInspectionRecordId("record-mask");
        InspectionRecord record = new InspectionRecord();
        record.setTenantCode("customer-a");
        record.setTaskId("task-mask");
        InspectionTask task = new InspectionTask();
        task.setTenantCode("customer-a");
        task.setProcessCode("SOLDER_MASK");
        when(sourceDocumentMapper.selectOne(any())).thenReturn(photo);
        when(inspectionRecordMapper.selectById("record-mask")).thenReturn(record);
        when(inspectionTaskMapper.selectById("task-mask")).thenReturn(task);
        doThrow(new AccessDeniedException("not assigned")).when(inspectionAccessScopeService).requireProcessAccess("SOLDER_MASK");
        InspectionAttachmentApplicationService service = new InspectionAttachmentApplicationService(attachmentStorage,
            sourceDocumentMapper, inspectionRecordMapper, inspectionTaskMapper, inspectionAccessScopeService);

        assertThatThrownBy(() -> service.load("attachment-mask"))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(attachmentStorage);
    }
}
