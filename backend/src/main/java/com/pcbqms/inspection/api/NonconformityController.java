package com.pcbqms.inspection.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.inspection.application.NonconformityApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NonconformityController {
    private final NonconformityApplicationService service;

    @GetMapping("/nonconformities")
    public ApiResponse<List<NonconformityResponse>> listNonconformities(@RequestParam(required = false) String status) {
        return ApiResponse.of(service.listNonconformities(status));
    }

    @PostMapping("/nonconformities")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NonconformityResponse> createNonconformity(@Valid @RequestBody CreateNonconformityRequest request) {
        return ApiResponse.of(service.createNonconformity(request));
    }

    @PostMapping("/nonconformities/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<NonconformityResponse> createManualNonconformity(@Valid @RequestBody CreateManualNonconformityRequest request) {
        return ApiResponse.of(service.createManualNonconformity(request));
    }

    @PostMapping("/nonconformities/{ncrId}/capas")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CapaResponse> createCapa(@PathVariable String ncrId, @Valid @RequestBody CreateCapaRequest request) {
        return ApiResponse.of(service.createCapa(ncrId, request));
    }

    @GetMapping("/capas")
    public ApiResponse<List<CapaResponse>> listCapas(@RequestParam(required = false) String status) {
        return ApiResponse.of(service.listCapas(status));
    }

    @PostMapping("/capas/{capaId}/close")
    public ApiResponse<CapaResponse> closeCapa(@PathVariable String capaId, @Valid @RequestBody CloseCapaRequest request) {
        return ApiResponse.of(service.closeCapa(capaId, request));
    }
}
