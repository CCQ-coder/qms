package com.pcbqms.gateway.infrastructure;

import com.pcbqms.gateway.config.GatewayProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class CursorStore {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final Path cursorFile;

    public CursorStore(GatewayProperties properties) {
        this.cursorFile = properties.getCursorFile().toAbsolutePath().normalize();
    }

    public LocalDateTime read() {
        try {
            if (!Files.exists(cursorFile)) {
                return LocalDateTime.now().minusDays(1);
            }
            return LocalDateTime.parse(Files.readString(cursorFile).trim(), FORMATTER);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("无法读取网关同步游标：" + cursorFile, exception);
        }
    }

    public void write(LocalDateTime cursor) {
        try {
            Files.createDirectories(cursorFile.getParent());
            Path temporaryFile = cursorFile.resolveSibling(cursorFile.getFileName() + ".tmp");
            Files.writeString(temporaryFile, FORMATTER.format(cursor));
            try {
                Files.move(temporaryFile, cursorFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, cursorFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存网关同步游标：" + cursorFile, exception);
        }
    }
}
