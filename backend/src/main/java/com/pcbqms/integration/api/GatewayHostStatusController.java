package com.pcbqms.integration.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.integration.application.GatewayHostStatusApplicationService;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gateway/host-status")
public class GatewayHostStatusController {
    private final GatewayIntegrationAuthentication authentication;
    private final GatewayHostStatusApplicationService service;

    public GatewayHostStatusController(GatewayIntegrationAuthentication authentication,
                                       GatewayHostStatusApplicationService service) {
        this.authentication = authentication;
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> record(@RequestHeader(value = "X-Gateway-Token", required = false) String token,
                                       @Valid @RequestBody GatewayHostStatusRequest request) {
        authentication.verify(token);
        service.record(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ApiResponse<List<GatewayHostStatusResponse>> listLatest() {
        return ApiResponse.of(service.listLatest());
    }
}
