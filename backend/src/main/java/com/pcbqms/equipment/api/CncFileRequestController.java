package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.CncProgramReleaseApplicationService;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cnc/file-requests")
@RequiredArgsConstructor
public class CncFileRequestController {
    private final GatewayIntegrationAuthentication gatewayIntegrationAuthentication;
    private final CncProgramReleaseApplicationService programReleaseApplicationService;

    @PostMapping
    public ApiResponse<CncFileRequestResponse> requestFile(
        @RequestHeader(value = "X-Gateway-Token", required = false) String gatewayToken,
        @Valid @RequestBody CncFileRequest request
    ) {
        gatewayIntegrationAuthentication.verify(gatewayToken);
        return ApiResponse.of(programReleaseApplicationService.requestFile(request));
    }
}
