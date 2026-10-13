package com.pcbqms.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.pcbqms.gateway.config.GatewayProperties;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorStoreTest {
    @Test
    void persistsAndRestoresCursor() {
        GatewayProperties properties = new GatewayProperties();
        Path temporaryDirectory = Path.of("target", "test-cursors", UUID.randomUUID().toString());
        properties.setCursorFile(temporaryDirectory.resolve("gateway/sync-cursor.txt"));
        CursorStore cursorStore = new CursorStore(properties);
        LocalDateTime cursor = LocalDateTime.of(2026, 9, 4, 9, 30, 15);

        cursorStore.write(cursor);

        assertThat(cursorStore.read()).isEqualTo(cursor);
    }
}
