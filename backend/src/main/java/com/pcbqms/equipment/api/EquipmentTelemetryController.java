package com.pcbqms.equipment.api;

import com.pcbqms.common.api.ApiResponse;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import com.pcbqms.equipment.application.EquipmentTelemetryQueryApplicationService;
import com.pcbqms.equipment.application.FinishedTestDeviceSummaryService;
import com.pcbqms.inspection.api.BindFlyingProbeWorkOrderRequest;
import com.pcbqms.inspection.application.FlyingProbeInspectionProjectionService;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.integration.application.GatewayIntegrationAuthentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/equipment/telemetry")
@RequiredArgsConstructor
@Slf4j
public class EquipmentTelemetryController {
    private final GatewayIntegrationAuthentication gatewayIntegrationAuthentication;
    private final EquipmentTelemetryApplicationService telemetryApplicationService;
    private final EquipmentTelemetryQueryApplicationService telemetryQueryApplicationService;
    private final FinishedTestDeviceSummaryService finishedTestDeviceSummaryService;
    private final FlyingProbeInspectionProjectionService flyingProbeProjectionService;
    private final InspectionAccessScopeService accessScopeService;
    private final JdbcTemplate jdbcTemplate;

    public record BatchWorkOrderRequest(@NotEmpty @Size(max = 1000) List<@NotBlank String> telemetryIds,
                                        String action, String workOrderNo) { }
    public record BatchWorkOrderResult(int changed, int skipped, int failed, List<String> reasons) { }

    @PostMapping("/batch-work-order")
    public ApiResponse<BatchWorkOrderResult> batchWorkOrder(@Valid @RequestBody BatchWorkOrderRequest request) {
        accessScopeService.requireSupervisorOrAdministrator();
        if (!"BIND".equals(request.action()) && !"CANCEL".equals(request.action())) {
            throw new IllegalArgumentException("批量操作类型无效");
        }
        if ("BIND".equals(request.action()) && (request.workOrderNo() == null
            || request.workOrderNo().isBlank() || request.workOrderNo().length() > 80)) {
            throw new IllegalArgumentException("请选择有效的生产指令");
        }
        int changed = 0, skipped = 0, failed = 0;
        List<String> reasons = new ArrayList<>();
        Set<String> runKeys = new LinkedHashSet<>();
        for (String id : new LinkedHashSet<>(request.telemetryIds())) {
            try {
                List<String> types = jdbcTemplate.queryForList("""
                    SELECT event_type FROM qms_equipment_telemetry WHERE id = ? AND tenant_code = ?
                    """, String.class, id, TenantContext.tenantCode());
                if (types.isEmpty()) throw new IllegalArgumentException("记录不存在或已过期");
                if ("FLYING_PROBE_TEST_COUNTERS".equals(types.getFirst())) {
                    if ("BIND".equals(request.action())) {
                        runKeys.add(flyingProbeProjectionService.bindManually(id, request.workOrderNo()));
                    } else flyingProbeProjectionService.unbindManually(id);
                } else if ("ZHONGBO_TEST_REPORT".equals(types.getFirst())) {
                    if ("BIND".equals(request.action())) {
                        finishedTestDeviceSummaryService.bindZhongboWorkOrder(id, request.workOrderNo());
                    } else finishedTestDeviceSummaryService.unbindZhongboWorkOrder(id);
                } else throw new IllegalArgumentException("此设备类型不支持关联生产指令");
                changed++;
            } catch (IllegalArgumentException exception) {
                skipped++;
                reasons.add(id + "：" + exception.getMessage());
            } catch (AccessDeniedException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                failed++;
                reasons.add(id + "：处理失败，请查看服务日志");
                log.warn("设备记录批量关联失败，记录={}", id, exception);
            }
        }
        for (String runKey : runKeys) {
            try { flyingProbeProjectionService.wakeManuallyBoundRun(runKey); }
            catch (RuntimeException ignored) { /* The scheduled retry will process this run. */ }
        }
        return ApiResponse.of(new BatchWorkOrderResult(changed, skipped, failed, reasons));
    }

    @GetMapping("/finished-test-summary")
    public ApiResponse<List<FinishedTestDeviceSummaryService.Summary>> finishedTestSummary() {
        return ApiResponse.of(finishedTestDeviceSummaryService.listSummary());
    }

    @GetMapping("/finished-test-summary/by-device")
    public ApiResponse<List<FinishedTestDeviceSummaryService.DeviceSummary>> finishedTestSummaryByDevice() {
        return ApiResponse.of(finishedTestDeviceSummaryService.listDeviceSummary());
    }

    @GetMapping("/finished-test-settlements")
    public ApiResponse<List<FinishedTestDeviceSummaryService.SettlementSnapshot>> finishedTestSettlements() {
        return ApiResponse.of(finishedTestDeviceSummaryService.listSettlementSnapshots());
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
        @RequestParam(required = false) String internalProductNo,
        @RequestParam(required = false) String factoryInternalNo,
        @RequestParam(required = false) Boolean unmatchedOnly
    ) {
        return ApiResponse.of(telemetryQueryApplicationService.pageHistory(
            new EquipmentTelemetryHistoryQuery(equipmentId, fromTime, toTime, cursor, limit, eventType,
                workOrderNo, internalProductNo, factoryInternalNo, unmatchedOnly)
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
