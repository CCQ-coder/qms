package com.pcbqms.inspection.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.inspection.application.InspectionTemplateApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspection-templates")
@RequiredArgsConstructor
public class InspectionTemplateController {
    private final InspectionTemplateApplicationService inspectionTemplateApplicationService;

    @GetMapping
    public ApiResponse<List<InspectionTemplateResponse>> list() {
        return ApiResponse.of(inspectionTemplateApplicationService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectionTemplateResponse> get(@PathVariable String id) {
        return ApiResponse.of(inspectionTemplateApplicationService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InspectionTemplateResponse> create(@Valid @RequestBody CreateInspectionTemplateRequest request) {
        return ApiResponse.of(inspectionTemplateApplicationService.create(request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<InspectionTemplateResponse> updateStatus(
        @PathVariable String id,
        @Valid @RequestBody UpdateInspectionTemplateStatusRequest request
    ) {
        return ApiResponse.of(inspectionTemplateApplicationService.updateStatus(id, request.status()));
    }
}
