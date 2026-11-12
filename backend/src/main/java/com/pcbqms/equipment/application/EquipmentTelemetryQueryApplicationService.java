package com.pcbqms.equipment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentTelemetryHistoryPageResponse;
import com.pcbqms.equipment.api.EquipmentTelemetryHistoryQuery;
import com.pcbqms.equipment.api.EquipmentTelemetryRecordResponse;
import com.pcbqms.equipment.api.ZhongboTestReportFields;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.domain.EquipmentTelemetryProjectionStatus;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetryRecordMapper;
import com.pcbqms.workorder.domain.WorkOrder;
import com.pcbqms.workorder.infrastructure.persistence.WorkOrderMapper;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EquipmentTelemetryQueryApplicationService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 300;

    private final EquipmentTelemetryRecordMapper telemetryRecordMapper;
    private final EquipmentMapper equipmentMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;
    private final ObjectMapper objectMapper;
    private final WorkOrderMapper workOrderMapper;
    @Value("${qms.equipment-telemetry.retention.raw-days:7}")
    private int rawRetentionDays;

    public List<EquipmentTelemetryRecordResponse> listRecent(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
        var access = inspectionAccessScopeService.currentAccess();
        String tenantCode = TenantContext.tenantCode();
        List<EquipmentTelemetryRecord> recent = telemetryRecordMapper.selectRecentByTenant(tenantCode, limit);
        Map<String, String> internalNumbers = internalNumbers(tenantCode,
            recent.stream().map(EquipmentTelemetryRecord::getWorkOrderNo).toList());
        return recent.stream()
            .map(record -> toResponse(record, tenantCode, access, internalNumbers))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    public EquipmentTelemetryHistoryPageResponse pageHistory(EquipmentTelemetryHistoryQuery query) {
        var access = inspectionAccessScopeService.currentAccess();
        String tenantCode = TenantContext.tenantCode();
        Map<String, Equipment> accessibleEquipment = equipmentMapper.selectList(Wrappers.lambdaQuery(Equipment.class)
                .eq(Equipment::getTenantCode, tenantCode))
            .stream()
            .filter(equipment -> access.fullDataAccess() || (StringUtils.hasText(equipment.getProcessCode())
                && access.canAccess(equipment.getProcessCode())))
            .collect(Collectors.toMap(Equipment::getId, equipment -> equipment));
        String selectedEquipmentId = StringUtils.hasText(query.equipmentId()) ? query.equipmentId().trim() : null;
        if (selectedEquipmentId != null && !accessibleEquipment.containsKey(selectedEquipmentId)) {
            return new EquipmentTelemetryHistoryPageResponse(List.of(), null, query.resolvedLimit());
        }
        if (selectedEquipmentId == null && accessibleEquipment.isEmpty()) {
            return new EquipmentTelemetryHistoryPageResponse(List.of(), null, query.resolvedLimit());
        }
        String eventType = StringUtils.hasText(query.eventType()) ? query.eventType().trim() : null;
        boolean finishedTestEvent = "ZHONGBO_TEST_REPORT".equals(eventType)
            || "FLYING_PROBE_TEST_COUNTERS".equals(eventType) || "FINISHED_TEST".equals(eventType);
        LocalDateTime toTime = query.toTime() == null && finishedTestEvent ? null
            : query.toTime() == null ? LocalDateTime.now() : query.toTime();
        LocalDateTime fromTime = query.fromTime() == null && !finishedTestEvent
            ? toTime.minusDays(Math.max(rawRetentionDays, 1)) : query.fromTime();
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new IllegalArgumentException("遥测历史开始时间不能晚于结束时间");
        }
        boolean sortByDeviceTime = "FLYING_PROBE_TEST_COUNTERS".equals(eventType);
        Cursor cursor = decodeCursor(query.cursor());
        int pageSize = query.resolvedLimit();
        List<EquipmentTelemetryRecord> rows = telemetryRecordMapper.selectHistory(
            tenantCode,
            selectedEquipmentId,
            selectedEquipmentId == null && !access.fullDataAccess() ? List.copyOf(accessibleEquipment.keySet()) : null,
            fromTime,
            toTime,
            cursor == null ? null : cursor.time(),
            cursor == null ? null : cursor.id(),
            pageSize + 1,
            eventType,
            StringUtils.hasText(query.workOrderNo()) ? query.workOrderNo().trim() : null,
            StringUtils.hasText(query.internalProductNo()) ? query.internalProductNo().trim() : null,
            StringUtils.hasText(query.factoryInternalNo()) ? query.factoryInternalNo().trim() : null,
            Boolean.TRUE.equals(query.unmatchedOnly()) && "ZHONGBO_TEST_REPORT".equals(eventType),
            Boolean.TRUE.equals(query.workOrderEmptyOnly())
        );
        boolean hasMore = rows.size() > pageSize;
        List<EquipmentTelemetryRecord> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<String> flyingProbeIds = pageRows.stream()
            .filter(record -> "FLYING_PROBE_TEST_COUNTERS".equals(record.getEventType()))
            .map(EquipmentTelemetryRecord::getId)
            .toList();
        Map<String, EquipmentTelemetryProjectionStatus> projections = flyingProbeIds.isEmpty()
            ? Map.of()
            : telemetryRecordMapper.selectProjectionStatuses(tenantCode, flyingProbeIds).stream()
                .collect(Collectors.toMap(EquipmentTelemetryProjectionStatus::getTelemetryId, item -> item));
        Map<String, String> internalNumbers = internalNumbers(tenantCode, pageRows.stream()
            .map(record -> projections.containsKey(record.getId()) && projections.get(record.getId()).getWorkOrderNo() != null
                ? projections.get(record.getId()).getWorkOrderNo() : record.getWorkOrderNo()).toList());
        List<EquipmentTelemetryRecordResponse> items = pageRows.stream()
            .map(record -> toResponse(record, accessibleEquipment.get(record.getEquipmentId()),
                projections.get(record.getId()), internalNumbers))
            .filter(Objects::nonNull)
            .toList();
        String nextCursor = hasMore && !pageRows.isEmpty() ? encodeCursor(pageRows.getLast(), sortByDeviceTime) : null;
        return new EquipmentTelemetryHistoryPageResponse(items, nextCursor, pageSize);
    }

    private EquipmentTelemetryRecordResponse toResponse(
        EquipmentTelemetryRecord record,
        String tenantCode,
        InspectionAccessScopeService.InspectionAccess access,
        Map<String, String> internalNumbers
    ) {
        Equipment equipment = equipmentMapper.selectById(record.getEquipmentId());
        if (equipment == null || !tenantCode.equals(equipment.getTenantCode())
            || (!access.fullDataAccess() && (!StringUtils.hasText(equipment.getProcessCode())
                || !access.canAccess(equipment.getProcessCode())))) {
            return null;
        }
        return toResponse(record, equipment, null, internalNumbers);
    }

    private EquipmentTelemetryRecordResponse toResponse(
        EquipmentTelemetryRecord record, Equipment equipment, EquipmentTelemetryProjectionStatus projection,
        Map<String, String> internalNumbers
    ) {
        if (equipment == null) return null;
        JsonNode attributes = attributesOf(record);
        String workOrderNo = projection != null && StringUtils.hasText(projection.getWorkOrderNo())
            ? projection.getWorkOrderNo() : record.getWorkOrderNo();
        return EquipmentTelemetryRecordResponse.from(
            record,
            equipment,
            textAttribute(attributes, "legacyState"),
            textAttribute(attributes, "legacyId"),
            textAttribute(attributes, "legacyMachineType"),
            decimalAttribute(attributes, "runtimeSeconds"),
            decimalAttribute(attributes, "routeLength"),
            textAttribute(attributes, "legacyScreenText") != null
                ? textAttribute(attributes, "legacyScreenText") : record.getAlarmMessage(),
            integerAttribute(attributes, "goodBoardCount"),
            integerAttribute(attributes, "badBoardCount"),
            projection,
            zhongboTestReport(record, attributes),
            workOrderNo == null ? null : internalNumbers.get(workOrderNo)
        );
    }

    private Map<String, String> internalNumbers(String tenantCode, List<String> workOrderNos) {
        List<String> usedNumbers = workOrderNos.stream().filter(StringUtils::hasText).distinct().toList();
        if (usedNumbers.isEmpty()) return Map.of();
        return workOrderMapper.selectList(Wrappers.lambdaQuery(WorkOrder.class)
            .eq(WorkOrder::getTenantCode, tenantCode)
            .in(WorkOrder::getWorkOrderNo, usedNumbers)).stream()
            .filter(order -> StringUtils.hasText(order.getInternalProductNo()))
            .collect(Collectors.toMap(WorkOrder::getWorkOrderNo, WorkOrder::getInternalProductNo, (first, ignored) -> first));
    }

    private ZhongboTestReportFields zhongboTestReport(EquipmentTelemetryRecord record, JsonNode attributes) {
        if (!"ZHONGBO_TEST_REPORT".equals(record.getEventType())) return null;
        return new ZhongboTestReportFields(
            textAttribute(attributes, "PortNumber"),
            longAttribute(attributes, "PanelsTested"),
            longAttribute(attributes, "ErrorTested"),
            longAttribute(attributes, "OpenErrors"),
            longAttribute(attributes, "ShortErrors"),
            longAttribute(attributes, "totalPoints"),
            decimalAttribute(attributes, "utilizationRate"),
            decimalAttribute(attributes, "utilizationDuration"),
            longAttribute(attributes, "Counts"),
            decimalAttribute(attributes, "openThresh"),
            decimalAttribute(attributes, "shortThresh"),
            longAttribute(attributes, "hit")
        );
    }

    private JsonNode attributesOf(EquipmentTelemetryRecord record) {
        if (!StringUtils.hasText(record.getRawPayload())) return null;
        try {
            return objectMapper.readTree(record.getRawPayload()).path("attributes");
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String textAttribute(JsonNode attributes, String name) {
        if (attributes == null) return null;
        JsonNode value = attributes.path(name);
        return value.isValueNode() && !value.isNull() ? value.asText() : null;
    }

    private BigDecimal decimalAttribute(JsonNode attributes, String name) {
        if (attributes == null) return null;
        JsonNode value = attributes.path(name);
        if (!value.isNumber()) return null;
        return value.decimalValue();
    }

    private Integer integerAttribute(JsonNode attributes, String name) {
        if (attributes == null) return null;
        JsonNode value = attributes.path(name);
        return value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
    }

    private Long longAttribute(JsonNode attributes, String name) {
        if (attributes == null) return null;
        JsonNode value = attributes.path(name);
        return value.isIntegralNumber() && value.canConvertToLong() ? value.longValue() : null;
    }

    private String encodeCursor(EquipmentTelemetryRecord record, boolean sortByDeviceTime) {
        LocalDateTime time = sortByDeviceTime ? record.getDeviceTime() : record.getReceivedAt();
        String value = time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "|" + record.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2 || !StringUtils.hasText(parts[1])) throw new IllegalArgumentException();
            return new Cursor(LocalDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME), parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("遥测历史分页游标无效");
        }
    }

    private record Cursor(LocalDateTime time, String id) {
    }
}
