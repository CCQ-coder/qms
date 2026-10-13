package com.pcbqms.integration.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.integration.application.OcrWorkOrderImportService;
import com.pcbqms.workorder.api.WorkOrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ocr/work-orders")
@RequiredArgsConstructor
public class OcrWorkOrderImportController {
    private final OcrWorkOrderImportService ocrWorkOrderImportService;

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OcrUploadResponse> upload(@RequestPart("image") MultipartFile image) {
        return ApiResponse.of(ocrWorkOrderImportService.upload(image));
    }

    @PostMapping("/{sourceDocumentId}/confirm")
    public ApiResponse<WorkOrderResponse> confirm(
        @PathVariable String sourceDocumentId,
        @Valid @org.springframework.web.bind.annotation.RequestBody OcrConfirmationRequest request
    ) {
        return ApiResponse.of(ocrWorkOrderImportService.confirm(sourceDocumentId, request));
    }
}

