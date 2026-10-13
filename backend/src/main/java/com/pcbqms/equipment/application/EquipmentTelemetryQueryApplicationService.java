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
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetryRecordMapper;
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
    @Value("${qms.equipment-telemetry.retention.raw-days:7}")
    private int rawRetentionDays;

    public List<EquipmentTelemetryRecordResponse> listRecent(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
        var access = inspectionAccessScopeService.currentAccess();
        String tenantCode = TenantContext.tenantCode();
        return telemetryRecordMapper.selectRecentByTenant(tenantCode, limit).stream()
            .map(record -> toResponse(record, tenantCode, access))
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
        LocalDateTime toTime = query.toTime() == null ? LocalDateTime.now() : query.toTime();
        LocalDateTime fromTime = query.fromTime() == null ? toTime.minusDays(Math.max(rawRetentionDays, 1)) : query.fromTime();
        if (fromTime.isAfter(toTime)) {
            throw new IllegalArgumentException("遥测历史开始时间不能晚于结束时间");
        }
        Cursor cursor = decodeCursor(query.cursor());
        int pageSize = query.resolvedLimit();
        List<EquipmentTelemetryRecord> rows = telemetryRecordMapper.selectHistory(
            tenantCode,
            selectedEquipmentId,
            selectedEquipmentId == null && !access.fullDataAccess() ? List.copyOf(accessibleEquipment.keySet()) : null,
            fromTime,
            toTime,
            cursor == null ? null : cursor.receivedAt(),
            cursor == null ? null : cursor.id(),
            pageSize + 1
        );
        boolean hasMore = rows.size() > pageSize;
        List<EquipmentTelemetryRecord> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        List<EquipmentTelemetryRecordResponse> items = pageRows.stream()
            .map(record -> toResponse(record, accessibleEquipment.get(record.getEquipmentId())))
            .filter(Objects::nonNull)
            .toList();
        String nextCursor = hasMore && !pageRows.isEmpty() ? encodeCursor(pageRows.getLast()) : null;
        return new EquipmentTelemetryHistoryPageResponse(items, nextCursor, pageSize);
    }

    private EquipmentTelemetryRecordResponse toResponse(
        EquipmentTelemetryRecord record,
        String tenantCode,
        InspectionAccessScopeService.InspectionAccess access
    ) {
        Equipment equipment = equipmentMapper.selectById(record.getEquipmentId());
        if (equipment == null || !tenantCode.equals(equipment.getTenantCode())
            || (!access.fullDataAccess() && (!StringUtils.hasText(equipment.getProcessCode())
                || !access.canAccess(equipment.getProcessCode())))) {
            return null;
        }
        return toResponse(record, equipment);
    }

    private EquipmentTelemetryRecordResponse toResponse(EquipmentTelemetryRecord record, Equipment equipment) {
        if (equipment == null) return null;
        JsonNode attributes = attributesOf(record);
        return EquipmentTelemetryRecordResponse.from(
            record,
            equipment,
            textAttribute(attributes, "legacyState"),
            textAttribute(attributes, "legacyId"),
            textAttribute(attributes, "legacyMachineType"),
            decimalAttribute(attributes, "runtimeSeconds"),
            decimalAttribute(attributes, "routeLength"),
            textAttribute(attributes, "legacyScreenText") != null
                ? textAttribute(attributes, "legacyScreenText") : record.getAlarmMessage()
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

    private String encodeCursor(EquipmentTelemetryRecord record) {
        String value = record.getReceivedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "|" + record.getId();
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

    private record Cursor(LocalDateTime receivedAt, String id) {
    }
}
