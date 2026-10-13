package com.pcbqms.workorder.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.inspection.api.GenerateInspectionTaskRequest;
import com.pcbqms.inspection.api.GenerationResultResponse;
import com.pcbqms.inspection.application.InspectionTaskGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders")
@RequiredArgsConstructor
public class InspectionTaskGenerationController {
    private final InspectionTaskGenerationService inspectionTaskGenerationService;

    @PostMapping("/{workOrderNo}/inspection-tasks/generate")
    public ApiResponse<GenerationResultResponse> generate(
        @PathVariable String workOrderNo,
        @RequestBody(required = false) GenerateInspectionTaskRequest request
    ) {
        return ApiResponse.of(inspectionTaskGenerationService.generateForWorkOrder(workOrderNo,
            request == null ? null : request.batchNo()));
    }
}

