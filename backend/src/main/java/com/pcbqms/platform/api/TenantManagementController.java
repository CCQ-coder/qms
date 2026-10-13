package com.pcbqms.platform.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.platform.application.TenantManagementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TenantManagementController {
    private final TenantManagementService tenantManagementService;

    @GetMapping
    public ApiResponse<List<TenantManagementResponse>> list() {
        return ApiResponse.of(tenantManagementService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TenantManagementResponse> create(@Valid @RequestBody SaveTenantRequest request) {
        return ApiResponse.of(tenantManagementService.create(request));
    }

    @PutMapping("/{code}")
    public ApiResponse<TenantManagementResponse> update(@PathVariable String code,
                                                         @Valid @RequestBody SaveTenantRequest request) {
        return ApiResponse.of(tenantManagementService.update(code, request));
    }

    @PutMapping("/{code}/login-pinned")
    public ApiResponse<TenantManagementResponse> updateLoginPinned(
        @PathVariable String code,
        @RequestBody UpdateTenantLoginPinnedRequest request
    ) {
        return ApiResponse.of(tenantManagementService.updateLoginPinned(code, request.loginPinned()));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        tenantManagementService.delete(code);
    }
}
