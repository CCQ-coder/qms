package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import com.pcbqms.equipment.application.EquipmentTelemetryQueryApplicationService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment/telemetry")
@RequiredArgsConstructor
public class EquipmentTelemetryController {
    private final GatewayIntegrationAuthentication gatewayIntegrationAuthentication;
    private final EquipmentTelemetryApplicationService telemetryApplicationService;
    private final EquipmentTelemetryQueryApplicationService telemetryQueryApplicationService;

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
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.of(telemetryQueryApplicationService.pageHistory(
            new EquipmentTelemetryHistoryQuery(equipmentId, fromTime, toTime, cursor, limit)
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
