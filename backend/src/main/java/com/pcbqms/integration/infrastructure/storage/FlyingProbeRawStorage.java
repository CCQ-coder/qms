package com.pcbqms.integration.infrastructure.storage;

import com.pcbqms.integration.application.FlyingProbeIniParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FlyingProbeRawStorage {
    private final Path root;

    public FlyingProbeRawStorage(@Value("${qms.attachment.local-root:uploads}") String localRoot) {
        root = Path.of(localRoot).toAbsolutePath().normalize().resolve("flying-probe");
    }

    public void store(String tenant, byte[] content) {
        Path directory = root.resolve(FlyingProbeIniParser.sha256(tenant.getBytes(StandardCharsets.UTF_8)));
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".upload-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, directory.resolve(FlyingProbeIniParser.sha256(content) + ".ini"),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("原始飞针文件保存失败", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException exception) { throw new IllegalStateException("飞针临时文件清理失败", exception); }
            }
        }
    }
}
