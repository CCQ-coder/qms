package com.pcbqms.inspection.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.administration.application.AdministrationApplicationService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.inspection.api.ScrapImportJobResponse;
import com.pcbqms.integration.domain.SourceDocument;
import com.pcbqms.integration.infrastructure.persistence.SourceDocumentMapper;
import com.pcbqms.integration.infrastructure.storage.LocalAttachmentStorage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ScrapExcelImportService {
    public static final String DOCUMENT_TYPE = "SCRAP_EXCEL";
    private final SourceDocumentMapper documentMapper;
    private final LocalAttachmentStorage storage;
    private final AdministrationApplicationService administrationService;
    private final ObjectMapper objectMapper;

    public ScrapImportJobResponse upload(MultipartFile file) {
        administrationService.requireScrapImportAccess();
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024
            || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("请上传不超过 10 MB 的 .xlsx 报废明细模板");
        }
        byte[] content;
        try { content = file.getBytes(); }
        catch (IOException exception) { throw new IllegalArgumentException("Excel 文件读取失败", exception); }
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') {
            throw new IllegalArgumentException("上传文件不是有效的 .xlsx 格式");
        }
        String hash = sha256(content);
        SourceDocument existing = findByHash(hash);
        if (existing != null) return response(retryIfFailed(existing));
        SourceDocument document = new SourceDocument();
        document.setId(UUID.randomUUID().toString());
        document.setTenantCode(TenantContext.tenantCode());
        document.setDocumentType(DOCUMENT_TYPE);
        document.setOriginalFilename(file.getOriginalFilename());
        document.setStoragePath(storage.store(TenantContext.tenantCode(), file));
        document.setContentHash(hash);
        document.setStatus("PENDING");
        document.setCreatedBy(TenantContext.actor());
        document.setCreatedAt(LocalDateTime.now());
        try {
            documentMapper.insert(document);
        } catch (DuplicateKeyException exception) {
            storage.delete(document.getStoragePath());
            SourceDocument duplicate = findByHash(hash);
            if (duplicate == null) throw new IllegalStateException("重复导入任务尚未就绪，请重试", exception);
            return response(retryIfFailed(duplicate));
        }
        return response(document);
    }

    public ScrapImportJobResponse get(String id) {
        administrationService.requireScrapImportAccess();
        SourceDocument document = documentMapper.selectById(id);
        if (document == null || !TenantContext.tenantCode().equals(document.getTenantCode())
            || !DOCUMENT_TYPE.equals(document.getDocumentType())) {
            throw new IllegalArgumentException("报废明细导入任务不存在或无权查看");
        }
        return response(document);
    }

    public List<ScrapImportJobResponse> recent() {
        administrationService.requireScrapImportAccess();
        return documentMapper.selectList(Wrappers.lambdaQuery(SourceDocument.class)
            .eq(SourceDocument::getTenantCode, TenantContext.tenantCode())
            .eq(SourceDocument::getDocumentType, DOCUMENT_TYPE)
            .orderByDesc(SourceDocument::getCreatedAt).last("LIMIT 20"))
            .stream().map(this::response).toList();
    }

    private SourceDocument findByHash(String hash) {
        return documentMapper.selectOne(Wrappers.lambdaQuery(SourceDocument.class)
            .eq(SourceDocument::getTenantCode, TenantContext.tenantCode())
            .eq(SourceDocument::getDocumentType, DOCUMENT_TYPE)
            .eq(SourceDocument::getContentHash, hash));
    }

    private SourceDocument retryIfFailed(SourceDocument document) {
        if ("FAILED".equals(document.getStatus())) {
            document.setStatus("PENDING");
            document.setExtractedPayloadJson(null);
            document.setProcessingUpdatedAt(null);
            documentMapper.updateById(document);
        }
        return document;
    }

    private ScrapImportJobResponse response(SourceDocument document) {
        ScrapImportProgress progress = ScrapImportProgress.empty();
        if (document.getExtractedPayloadJson() != null) {
            try { progress = objectMapper.readValue(document.getExtractedPayloadJson(), ScrapImportProgress.class); }
            catch (IOException exception) { throw new IllegalStateException("导入任务进度无法读取", exception); }
        }
        return new ScrapImportJobResponse(document.getId(), document.getOriginalFilename(), document.getStatus(),
            progress.total(), progress.processed(), progress.imported(), progress.unmatched(), progress.invalid(),
            progress.errors(), document.getCreatedAt());
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 不可用", exception); }
    }
}
