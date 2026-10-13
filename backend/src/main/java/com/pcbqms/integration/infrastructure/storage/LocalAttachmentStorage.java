package com.pcbqms.integration.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Development storage adapter. Production replaces this component with S3/OSS object storage.
 */
@Component
public class LocalAttachmentStorage {
    private final Path rootPath;

    public LocalAttachmentStorage(@Value("${qms.attachment.local-root:uploads}") String localRoot) {
        this.rootPath = Path.of(localRoot).toAbsolutePath().normalize();
    }

    public String store(String tenantCode, MultipartFile file) {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "erp-screenshot" : file.getOriginalFilename());
        String extension = "";
        int extensionIndex = originalFilename.lastIndexOf('.');
        if (extensionIndex >= 0) {
            extension = originalFilename.substring(extensionIndex).replaceAll("[^a-zA-Z0-9.]", "");
        }
        String savedFilename = UUID.randomUUID() + extension;
        Path targetDirectory = rootPath.resolve(tenantCode).normalize();
        Path targetFile = targetDirectory.resolve(savedFilename).normalize();
        if (!targetFile.startsWith(rootPath)) {
            throw new IllegalArgumentException("附件路径不合法");
        }
        try {
            Files.createDirectories(targetDirectory);
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            return rootPath.relativize(targetFile).toString().replace('\\', '/');
        } catch (IOException exception) {
            throw new IllegalArgumentException("附件保存失败", exception);
        }
    }

    public Path resolve(String storedPath) {
        Path targetFile = rootPath.resolve(storedPath).normalize();
        if (!targetFile.startsWith(rootPath) || !Files.isRegularFile(targetFile)) {
            throw new IllegalArgumentException("附件不存在或路径不合法");
        }
        return targetFile;
    }
}
