package com.pcbqms.inspection.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.inspection.application.ScrapExcelImportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/nonconformities/scrap-imports")
@RequiredArgsConstructor
public class ScrapExcelImportController {
    private final ScrapExcelImportService service;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ScrapImportJobResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.of(service.upload(file));
    }

    @GetMapping
    public ApiResponse<List<ScrapImportJobResponse>> recent() {
        return ApiResponse.of(service.recent());
    }

    @GetMapping("/{id}")
    public ApiResponse<ScrapImportJobResponse> get(@PathVariable String id) {
        return ApiResponse.of(service.get(id));
    }
}
