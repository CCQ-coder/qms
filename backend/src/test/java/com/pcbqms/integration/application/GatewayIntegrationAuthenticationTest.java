package com.pcbqms.integration.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class GatewayIntegrationAuthenticationTest {
    @Test
    void rejectsGatewayRequestsWhenServerTokenIsNotConfigured() {
        GatewayIntegrationAuthentication authentication = new GatewayIntegrationAuthentication("");

        assertThatThrownBy(() -> authentication.verify("any-token"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void acceptsOnlyTheConfiguredSharedToken() {
        GatewayIntegrationAuthentication authentication = new GatewayIntegrationAuthentication("gateway-secret");

        assertThatCode(() -> authentication.verify("gateway-secret")).doesNotThrowAnyException();
        assertThatThrownBy(() -> authentication.verify("wrong-secret"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void isConfiguredReportsBlankToken() {
        assertThat(new GatewayIntegrationAuthentication(" ").isConfigured()).isFalse();
        assertThat(new GatewayIntegrationAuthentication("gateway-secret").isConfigured()).isTrue();
    }
}
