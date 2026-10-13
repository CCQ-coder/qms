package com.pcbqms.platform.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.platform.application.TenantParameterApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/parameters")
@RequiredArgsConstructor
public class TenantParameterController {
    private final TenantParameterApplicationService parameterApplicationService;

    @GetMapping
    public ApiResponse<TenantParameterResponse> get(@RequestParam String key) {
        return ApiResponse.of(parameterApplicationService.get(key));
    }

    @PutMapping
    public ApiResponse<TenantParameterResponse> update(
        @RequestParam String key,
        @Valid @RequestBody UpdateTenantParameterRequest request
    ) {
        return ApiResponse.of(parameterApplicationService.update(key, request.value()));
    }
}

