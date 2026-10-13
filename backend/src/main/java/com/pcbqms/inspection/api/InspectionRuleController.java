package com.pcbqms.inspection.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.inspection.application.InspectionRuleApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspection-rules")
@RequiredArgsConstructor
public class InspectionRuleController {
    private final InspectionRuleApplicationService inspectionRuleApplicationService;

    @GetMapping
    public ApiResponse<List<InspectionRuleResponse>> list() {
        return ApiResponse.of(inspectionRuleApplicationService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InspectionRuleResponse> create(@Valid @RequestBody CreateInspectionRuleRequest request) {
        return ApiResponse.of(inspectionRuleApplicationService.create(request));
    }
}

