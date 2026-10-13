package com.pcbqms.masterdata.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.masterdata.application.MasterDataApplicationService;
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
@RequestMapping("/api/v1/master-data")
@RequiredArgsConstructor
public class MasterDataController {
    private final MasterDataApplicationService masterDataApplicationService;

    @GetMapping("/products")
    public ApiResponse<List<ProductResponse>> listProducts() {
        return ApiResponse.of(masterDataApplicationService.listProducts());
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.of(masterDataApplicationService.createProduct(request));
    }

    @PatchMapping("/products/{id}/status")
    public ApiResponse<ProductResponse> updateProductStatus(
        @PathVariable String id,
        @Valid @RequestBody UpdateMasterDataStatusRequest request
    ) {
        return ApiResponse.of(masterDataApplicationService.updateProductStatus(id, request.status()));
    }

    @GetMapping("/processes")
    public ApiResponse<List<ProcessResponse>> listProcesses() {
        return ApiResponse.of(masterDataApplicationService.listProcesses());
    }

    @PostMapping("/processes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProcessResponse> createProcess(@Valid @RequestBody CreateProcessRequest request) {
        return ApiResponse.of(masterDataApplicationService.createProcess(request));
    }

    @PatchMapping("/processes/{id}/status")
    public ApiResponse<ProcessResponse> updateProcessStatus(
        @PathVariable String id,
        @Valid @RequestBody UpdateMasterDataStatusRequest request
    ) {
        return ApiResponse.of(masterDataApplicationService.updateProcessStatus(id, request.status()));
    }
}
