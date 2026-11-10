package com.pcbqms.inspection.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.inspection.application.FlyingProbeInspectionProjectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/inspection/flying-probe-projections")
@RequiredArgsConstructor
@Slf4j
public class FlyingProbeProjectionController {
    private final FlyingProbeInspectionProjectionService projectionService;

    @GetMapping("/retryable")
    public ApiResponse<FlyingProbeRetryablePageResponse> listRetryable(
        @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize,
        @RequestParam(required = false) String workOrderNo,
        @RequestParam(required = false) String internalProductNo,
        @RequestParam(required = false) String factoryInternalNo,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime
    ) {
        return ApiResponse.of(projectionService.listRetryable(page, pageSize, workOrderNo, internalProductNo,
            factoryInternalNo, fromTime, toTime));
    }

    @PostMapping("/{telemetryId}/bind-work-order")
    public ApiResponse<String> bindWorkOrder(
        @PathVariable String telemetryId, @Valid @RequestBody BindFlyingProbeWorkOrderRequest request
    ) {
        String runKey = projectionService.bindManually(telemetryId, request.workOrderNo());
        try {
            projectionService.wakeManuallyBoundRun(runKey);
        } catch (RuntimeException exception) {
            log.warn("飞针工单已绑定，但同文件快照立即重试失败，遥测={}", telemetryId, exception);
        }
        return ApiResponse.of(request.workOrderNo().trim());
    }
}
