package com.pcbqms.inspection.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.inspection.application.InspectionApplicationService;
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
@RequestMapping("/api/v1/inspection-tasks")
@RequiredArgsConstructor
public class InspectionTaskController {
    private final InspectionApplicationService inspectionApplicationService;

    @GetMapping
    public ApiResponse<List<InspectionTaskResponse>> list(@RequestParam(required = false) String status) {
        return ApiResponse.of(inspectionApplicationService.list(status));
    }

    @GetMapping("/completed-process-records")
    public ApiResponse<List<ProcessInspectionRecordResponse>> listCompletedProcessRecords() {
        return ApiResponse.of(inspectionApplicationService.listCompletedProcessRecords());
    }

    @GetMapping("/completed-finished-records")
    public ApiResponse<FinishedTestRecordPage> listCompletedFinishedRecords(
        @RequestParam(defaultValue = "100") int limit, @RequestParam(required = false) String cursor
    ) {
        return ApiResponse.of(inspectionApplicationService.listCompletedFinishedRecords(limit, cursor));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InspectionTaskResponse> createManualTask(@Valid @RequestBody CreateInspectionTaskRequest request) {
        return ApiResponse.of(inspectionApplicationService.createManualTask(request));
    }

    @PostMapping("/{taskId}/records")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InspectionRecordResponse> submitRecord(
        @PathVariable String taskId,
        @Valid @RequestBody CreateInspectionRecordRequest request
    ) {
        return ApiResponse.of(inspectionApplicationService.submitRecord(taskId, request));
    }
}
