package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import com.pcbqms.equipment.application.EquipmentTelemetryQueryApplicationService;
import com.pcbqms.equipment.application.FinishedTestDeviceSummaryService;
import com.pcbqms.inspection.api.BindFlyingProbeWorkOrderRequest;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/telemetry")
@RequiredArgsConstructor
public class EquipmentTelemetryController {
    private final GatewayIntegrationAuthentication gatewayIntegrationAuthentication;
    private final EquipmentTelemetryApplicationService telemetryApplicationService;
    private final EquipmentTelemetryQueryApplicationService telemetryQueryApplicationService;
    private final FinishedTestDeviceSummaryService finishedTestDeviceSummaryService;

    @GetMapping("/finished-test-summary")
    public ApiResponse<List<FinishedTestDeviceSummaryService.Summary>> finishedTestSummary() {
        return ApiResponse.of(finishedTestDeviceSummaryService.listSummary());
    }

    @GetMapping("/finished-test-monthly-analytics")
    public ApiResponse<FinishedTestDeviceSummaryService.MonthlyAnalytics> finishedTestMonthlyAnalytics() {
        return ApiResponse.of(finishedTestDeviceSummaryService.monthlyAnalytics());
    }

    @GetMapping("/today-bad-boards-by-material")
    public ApiResponse<FinishedTestDeviceSummaryService.DailyMaterialBadBoards> todayBadBoardsByMaterial() {
        return ApiResponse.of(finishedTestDeviceSummaryService.todayBadBoards());
    }

    @PostMapping("/{telemetryId}/bind-work-order")
    public ApiResponse<String> bindZhongboWorkOrder(
        @PathVariable String telemetryId, @Valid @RequestBody BindFlyingProbeWorkOrderRequest request
    ) {
        return ApiResponse.of(finishedTestDeviceSummaryService.bindZhongboWorkOrder(telemetryId, request.workOrderNo()));
    }

    @GetMapping
    public ApiResponse<List<EquipmentTelemetryRecordResponse>> listRecent(@RequestParam(required = false) Integer limit) {
        return ApiResponse.of(telemetryQueryApplicationService.listRecent(limit));
    }

    @GetMapping("/history")
    public ApiResponse<EquipmentTelemetryHistoryPageResponse> pageHistory(
        @RequestParam(required = false) String equipmentId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromTime,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toTime,
        @RequestParam(required = false) String cursor,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) String workOrderNo,
        @RequestParam(required = false) String internalProductNo
    ) {
        return ApiResponse.of(telemetryQueryApplicationService.pageHistory(
            new EquipmentTelemetryHistoryQuery(equipmentId, fromTime, toTime, cursor, limit, eventType, workOrderNo, internalProductNo)
        ));
    }

    @PostMapping
    public ApiResponse<EquipmentTelemetryResponse> receive(
        @RequestHeader(value = "X-Gateway-Token", required = false) String gatewayToken,
        @Valid @RequestBody EquipmentTelemetryRequest request
    ) {
        gatewayIntegrationAuthentication.verify(gatewayToken);
        return ApiResponse.of(telemetryApplicationService.receive(request));
    }
}
