package com.pcbqms.integration.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.integration.api.OcrConfirmationRequest;
import com.pcbqms.integration.api.OcrUploadResponse;
import com.pcbqms.integration.domain.SourceDocument;
import com.pcbqms.integration.infrastructure.persistence.SourceDocumentMapper;
import com.pcbqms.integration.infrastructure.storage.LocalAttachmentStorage;
import com.pcbqms.workorder.api.CreateWorkOrderRequest;
import com.pcbqms.workorder.api.WorkOrderResponse;
import com.pcbqms.workorder.application.WorkOrderApplicationService;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OcrWorkOrderImportService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final LocalAttachmentStorage attachmentStorage;
    private final SourceDocumentMapper sourceDocumentMapper;
    private final WorkOrderApplicationService workOrderApplicationService;
    private final OcrExtractionClient ocrExtractionClient;
    private final ObjectMapper objectMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    @Transactional
    public OcrUploadResponse upload(MultipartFile image) {
        inspectionAccessScopeService.currentAccess();
        if (image.isEmpty()) {
            throw new IllegalArgumentException("请上传 ERP 工单截图");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(image.getContentType())) {
            throw new IllegalArgumentException("仅支持 JPG、PNG 或 WEBP 格式的工单截图");
        }
        String tenantCode = TenantContext.tenantCode();
        String path = attachmentStorage.store(tenantCode, image);
        OcrExtractionClient.OcrExtractionResult extraction = ocrExtractionClient.extract(image);
        SourceDocument document = new SourceDocument();
        document.setId(UUID.randomUUID().toString());
        document.setTenantCode(tenantCode);
        document.setDocumentType("ERP_WORK_ORDER_SCREENSHOT");
        document.setOriginalFilename(image.getOriginalFilename() == null ? "erp-screenshot" : image.getOriginalFilename());
        document.setStoragePath(path);
        document.setStatus("PENDING_REVIEW");
        document.setExtractedPayloadJson(toJson(extraction));
        document.setCreatedBy(TenantContext.actor());
        document.setCreatedAt(LocalDateTime.now());
        sourceDocumentMapper.insert(document);
        return new OcrUploadResponse(document.getId(), document.getOriginalFilename(), document.getStatus(), extraction.fields(),
            extraction.fields().isEmpty()
                ? "截图已保存，请人工填写并确认工单字段。"
                : "截图已识别，请核对预填字段后确认导入。"
        );
    }

    @Transactional
    public WorkOrderResponse confirm(String sourceDocumentId, OcrConfirmationRequest request) {
        var access = inspectionAccessScopeService.currentAccess();
        SourceDocument document = sourceDocumentMapper.selectById(sourceDocumentId);
        if (document == null || !TenantContext.tenantCode().equals(document.getTenantCode())) {
            throw new IllegalArgumentException("工单截图不存在或无权操作");
        }
        if (!access.fullDataAccess() && !TenantContext.actor().equals(document.getCreatedBy())) {
            throw new AccessDeniedException("仅可确认本人上传的工单截图");
        }
        if ("CONFIRMED".equals(document.getStatus())) {
            throw new IllegalArgumentException("该截图已完成确认");
        }
        inspectionAccessScopeService.requireProcessAccess(request.currentProcessCode().trim());
        WorkOrderResponse workOrder = workOrderApplicationService.upsertFromOcrConfirmation(new CreateWorkOrderRequest(
            request.workOrderNo(), request.productCode(), request.productName(), request.specification(),
            request.currentProcessCode(), request.currentProcessName(), request.plannedQuantity(), request.batchNo(),
            "OCR", document.getId(), "manual-confirmed",
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null
        ));
        document.setStatus("CONFIRMED");
        document.setWorkOrderId(workOrder.id());
        document.setConfirmedPayloadJson(toJson(request));
        document.setConfirmedAt(LocalDateTime.now());
        sourceDocumentMapper.updateById(document);
        return workOrder;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("工单确认内容无法保存", exception);
        }
    }
}
