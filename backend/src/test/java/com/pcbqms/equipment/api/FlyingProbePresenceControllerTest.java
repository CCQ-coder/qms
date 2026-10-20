package com.pcbqms.equipment.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pcbqms.equipment.application.FlyingProbePresenceApplicationService;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FlyingProbePresenceControllerTest {
    @Mock private FlyingProbePresenceApplicationService presenceService;

    @Test
    void rejectsAuthorizationHeaderEvenOutsideProductionFilter() {
        var controller = new FlyingProbePresenceController(
            new GatewayIntegrationAuthentication("gateway-secret"), presenceService);
        var request = new FlyingProbePresenceRequest("WTD-1", OffsetDateTime.parse("2026-10-20T09:00:00+08:00"),
            "ONLINE", "READABLE", null);

        assertThatThrownBy(() -> controller.receive("gateway-secret", "Bearer still-valid", request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(presenceService);
    }
}
