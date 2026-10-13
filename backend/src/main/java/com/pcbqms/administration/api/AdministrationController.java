package com.pcbqms.administration.api;

import com.pcbqms.administration.application.AdministrationApplicationService;
import com.pcbqms.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/administration")
@RequiredArgsConstructor
public class AdministrationController {
    private final AdministrationApplicationService administrationApplicationService;

    @GetMapping("/items/{catalogType}")
    public ApiResponse<List<AdministrationItemResponse>> listItems(@PathVariable String catalogType) {
        return ApiResponse.of(administrationApplicationService.listItems(catalogType));
    }

    @GetMapping("/menu-access")
    public ApiResponse<MenuAccessResponse> menuAccess() {
        return ApiResponse.of(administrationApplicationService.menuAccess());
    }

    @PostMapping("/items/{catalogType}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdministrationItemResponse> createItem(
        @PathVariable String catalogType,
        @Valid @RequestBody SaveAdministrationItemRequest request
    ) {
        return ApiResponse.of(administrationApplicationService.createItem(catalogType, request));
    }

    @PutMapping("/items/{catalogType}/{id}")
    public ApiResponse<AdministrationItemResponse> updateItem(
        @PathVariable String catalogType,
        @PathVariable String id,
        @Valid @RequestBody SaveAdministrationItemRequest request
    ) {
        return ApiResponse.of(administrationApplicationService.updateItem(catalogType, id, request));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdministrationAuditLogResponse>> listAuditLogs(
        @RequestParam(required = false) String module,
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.of(administrationApplicationService.listAuditLogs(module, limit));
    }
}
