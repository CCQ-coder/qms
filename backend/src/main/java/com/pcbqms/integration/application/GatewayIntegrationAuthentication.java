package com.pcbqms.integration.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GatewayIntegrationAuthentication {
    private final String sharedToken;

    public GatewayIntegrationAuthentication(@Value("${qms.integration.gateway.shared-token:}") String sharedToken) {
        this.sharedToken = sharedToken;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(sharedToken);
    }

    public void verify(String suppliedToken) {
        if (!isConfigured()) {
            throw new AccessDeniedException("数据同步网关认证未配置");
        }
        byte[] expected = sharedToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = (suppliedToken == null ? "" : suppliedToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new AccessDeniedException("数据同步网关认证失败");
        }
    }
}
