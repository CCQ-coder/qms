package com.pcbqms.gateway.config;

import java.nio.file.Path;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    private String qmsBaseUrl;
    private String tenantCode;
    private String sharedToken;
    private String bearerToken;
    private long fixedDelayMs;
    private long cursorOverlapSeconds;
    private Path cursorFile;
    private String sourceSql;
}
