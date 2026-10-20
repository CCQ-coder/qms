package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.FlyingProbePresenceApplicationService;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/equipment/presence/flying-probe")
@RequiredArgsConstructor
public class FlyingProbePresenceController {
    private final GatewayIntegrationAuthentication gatewayAuthentication;
    private final FlyingProbePresenceApplicationService presenceService;

    @PostMapping
    public ApiResponse<FlyingProbePresenceResponse> receive(
        @RequestHeader(value = "X-Gateway-Token", required = false) String gatewayToken,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody FlyingProbePresenceRequest request
    ) {
        if (authorization != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        gatewayAuthentication.verify(gatewayToken);
        return ApiResponse.of(presenceService.receive(request));
    }
}
