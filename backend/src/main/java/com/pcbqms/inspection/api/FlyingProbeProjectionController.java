package com.pcbqms.inspection.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.inspection.application.FlyingProbeInspectionProjectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;

@RestController
@RequestMapping("/api/v1/inspection/flying-probe-projections")
@RequiredArgsConstructor
@Slf4j
public class FlyingProbeProjectionController {
    private final FlyingProbeInspectionProjectionService projectionService;

    public record BatchBindRequest(@NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 80) String> telemetryIds,
                                   @NotBlank @Size(max = 80) String workOrderNo) { }
    public record BatchBindResponse(int bound, int skipped, int failed) { }

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

    @PostMapping("/batch-bind-work-order")
    public ApiResponse<BatchBindResponse> batchBindWorkOrder(@Valid @RequestBody BatchBindRequest request) {
        int bound = 0;
        int skipped = 0;
        int failed = 0;
        Set<String> runKeys = new LinkedHashSet<>();
        for (String telemetryId : request.telemetryIds().stream().distinct().toList()) {
            try {
                runKeys.add(projectionService.bindRetryableUnboundManually(telemetryId, request.workOrderNo()));
                bound++;
            } catch (IllegalArgumentException exception) {
                skipped++;
            } catch (AccessDeniedException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                failed++;
                log.warn("飞针手动批量绑定失败，遥测={}", telemetryId, exception);
            }
        }
        for (String runKey : runKeys) {
            try {
                projectionService.wakeManuallyBoundRun(runKey);
            } catch (RuntimeException exception) {
                log.warn("飞针工单已绑定，但同文件快照立即重试失败，文件={}", runKey, exception);
            }
        }
        return ApiResponse.of(new BatchBindResponse(bound, skipped, failed));
    }
}
