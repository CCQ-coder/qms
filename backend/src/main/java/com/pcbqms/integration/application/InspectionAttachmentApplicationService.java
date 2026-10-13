package com.pcbqms.integration.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.integration.api.InspectionAttachmentResponse;
import com.pcbqms.integration.domain.SourceDocument;
import com.pcbqms.integration.infrastructure.persistence.SourceDocumentMapper;
import com.pcbqms.integration.infrastructure.storage.LocalAttachmentStorage;
import com.pcbqms.inspection.domain.InspectionRecord;
import com.pcbqms.inspection.domain.InspectionTask;
import com.pcbqms.inspection.infrastructure.persistence.InspectionRecordMapper;
import com.pcbqms.inspection.infrastructure.persistence.InspectionTaskMapper;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class InspectionAttachmentApplicationService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private final LocalAttachmentStorage attachmentStorage;
    private final SourceDocumentMapper sourceDocumentMapper;
    private final InspectionRecordMapper inspectionRecordMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    @Transactional
    public InspectionAttachmentResponse upload(MultipartFile image) {
        if (image.isEmpty() || image.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("检验照片不能为空，且单张不能超过 10 MB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(image.getContentType())) {
            throw new IllegalArgumentException("检验照片仅支持 JPG、PNG 或 WEBP 格式");
        }
        String tenantCode = TenantContext.tenantCode();
        SourceDocument document = new SourceDocument();
        document.setId(UUID.randomUUID().toString());
        document.setTenantCode(tenantCode);
        document.setDocumentType("INSPECTION_PHOTO");
        document.setOriginalFilename(image.getOriginalFilename() == null ? "inspection-photo" : image.getOriginalFilename());
        document.setStoragePath(attachmentStorage.store(tenantCode, image));
        document.setStatus("UPLOADED");
        document.setCreatedBy(TenantContext.actor());
        document.setCreatedAt(LocalDateTime.now());
        sourceDocumentMapper.insert(document);
        return new InspectionAttachmentResponse(document.getId(), document.getOriginalFilename(), "/api/v1/attachments/" + document.getId());
    }

    @Transactional
    public void linkToInspectionRecord(String inspectionRecordId, List<String> attachmentUrls) {
        if (attachmentUrls == null || attachmentUrls.isEmpty()) {
            return;
        }
        for (String url : attachmentUrls) {
            String documentId = extractDocumentId(url);
            SourceDocument document = sourceDocumentMapper.selectOne(Wrappers.lambdaQuery(SourceDocument.class)
                .eq(SourceDocument::getId, documentId)
                .eq(SourceDocument::getTenantCode, TenantContext.tenantCode())
                .eq(SourceDocument::getDocumentType, "INSPECTION_PHOTO"));
            if (document == null || document.getInspectionRecordId() != null) {
                throw new IllegalArgumentException("检验照片不存在、无权使用或已关联其他记录");
            }
            document.setInspectionRecordId(inspectionRecordId);
            document.setStatus("LINKED");
            sourceDocumentMapper.updateById(document);
        }
    }

    public AttachmentFile load(String id) {
        SourceDocument document = sourceDocumentMapper.selectOne(Wrappers.lambdaQuery(SourceDocument.class)
            .eq(SourceDocument::getId, id)
            .eq(SourceDocument::getTenantCode, TenantContext.tenantCode()));
        if (document == null) {
            throw new IllegalArgumentException("附件不存在或无权访问");
        }
        assertCanRead(document);
        Path file = attachmentStorage.resolve(document.getStoragePath());
        return new AttachmentFile(document.getOriginalFilename(), file);
    }

    private void assertCanRead(SourceDocument document) {
        if ("ERP_WORK_ORDER_SCREENSHOT".equals(document.getDocumentType())) {
            var access = inspectionAccessScopeService.currentAccess();
            if (!access.fullDataAccess() && !TenantContext.actor().equals(document.getCreatedBy())) {
                throw new org.springframework.security.access.AccessDeniedException("仅可查看本人上传的工单截图");
            }
            return;
        }
        if (!"INSPECTION_PHOTO".equals(document.getDocumentType())) {
            throw new org.springframework.security.access.AccessDeniedException("不支持访问此类附件");
        }
        if (!StringUtils.hasText(document.getInspectionRecordId())) {
            if (!TenantContext.actor().equals(document.getCreatedBy())) {
                throw new org.springframework.security.access.AccessDeniedException("无权访问其他用户暂存的检验照片");
            }
            return;
        }
        InspectionRecord record = inspectionRecordMapper.selectById(document.getInspectionRecordId());
        if (record == null || !TenantContext.tenantCode().equals(record.getTenantCode())) {
            throw new IllegalArgumentException("检验照片关联的检验记录不存在");
        }
        InspectionTask task = inspectionTaskMapper.selectById(record.getTaskId());
        if (task == null || !TenantContext.tenantCode().equals(task.getTenantCode())) {
            throw new IllegalArgumentException("检验照片关联的检验任务不存在");
        }
        inspectionAccessScopeService.requireProcessAccess(task.getProcessCode());
    }

    private String extractDocumentId(String url) {
        String prefix = "/api/v1/attachments/";
        if (url == null || !url.startsWith(prefix) || url.length() <= prefix.length() || url.substring(prefix.length()).contains("/")) {
            throw new IllegalArgumentException("检验照片地址不合法");
        }
        return url.substring(prefix.length());
    }

    public record AttachmentFile(String originalFilename, Path file) {
    }
}
