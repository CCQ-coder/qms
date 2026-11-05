package com.pcbqms.integration.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GatewayIntegrationAuthentication {
    private final List<byte[]> sharedTokens;

    public GatewayIntegrationAuthentication(@Value("${qms.integration.gateway.shared-token:}") String sharedToken) {
        this.sharedTokens = Arrays.stream(sharedToken.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(token -> token.getBytes(StandardCharsets.UTF_8))
            .toList();
    }

    public boolean isConfigured() {
        return !sharedTokens.isEmpty();
    }

    public void verify(String suppliedToken) {
        if (!isConfigured()) {
            throw new AccessDeniedException("数据同步网关认证未配置");
        }
        byte[] supplied = (suppliedToken == null ? "" : suppliedToken).getBytes(StandardCharsets.UTF_8);
        boolean valid = false;
        for (byte[] expected : sharedTokens) {
            valid |= MessageDigest.isEqual(expected, supplied);
        }
        if (!valid) {
            throw new AccessDeniedException("数据同步网关认证失败");
        }
    }
}
