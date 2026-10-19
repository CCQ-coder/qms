package com.pcbqms.integration.btfhessian;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentTelemetryEventRequest;
import com.pcbqms.equipment.api.EquipmentTelemetryRequest;
import com.pcbqms.equipment.api.EquipmentTelemetryResponse;
import com.pcbqms.equipment.application.EquipmentGatewayMappingApplicationService;
import com.pcbqms.equipment.application.EquipmentGatewayUnmatchedMessageApplicationService;
import com.pcbqms.equipment.application.EquipmentTelemetryApplicationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Converts the JSON string carried by the old Hessian Monitor call into QMS telemetry. */
@Service
@RequiredArgsConstructor
public class BtfHessianMonitorApplicationService {
    private static final int MAX_EVENTS = 500;
    private static final Logger log = LoggerFactory.getLogger(BtfHessianMonitorApplicationService.class);
    private static final DateTimeFormatter LEGACY_TIME_FORMAT = DateTimeFormatter
        .ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);

    private final ObjectMapper objectMapper;
    private final EquipmentTelemetryApplicationService telemetryApplicationService;
    private final EquipmentGatewayMappingApplicationService gatewayMappingService;
    private final EquipmentGatewayUnmatchedMessageApplicationService unmatchedMessageApplicationService;
    private final BtfHessianProperties properties;

    public EquipmentTelemetryResponse receive(String payload) {
        List<EquipmentTelemetryEventRequest> events = parseEvents(payload);
        TenantContext.set(properties.getTenantCode(), "btf-hessian.monitor");
        try {
            List<EquipmentTelemetryEventRequest> mappedEvents = events.stream()
                .filter(this::hasEquipmentMapping)
                .toList();
            if (mappedEvents.isEmpty()) return new EquipmentTelemetryResponse(0, 0);
            return telemetryApplicationService.receive(new EquipmentTelemetryRequest(mappedEvents));
        } finally {
            TenantContext.clear();
        }
    }

    private boolean hasEquipmentMapping(EquipmentTelemetryEventRequest event) {
        try {
            gatewayMappingService.resolve(event.username(), event.machineName());
            return true;
        } catch (EquipmentGatewayMappingApplicationService.MissingEquipmentGatewayMappingException exception) {
            unmatchedMessageApplicationService.record(
                event.username(), event.machineName(), event.deviceTime(), serialize(event));
            log.debug("Skipped unmapped BTF monitor snapshot for machineName={}", event.machineName());
            return false;
        }
    }

    private String serialize(EquipmentTelemetryEventRequest event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存未映射设备的现场报文", exception);
        }
    }

    private List<EquipmentTelemetryEventRequest> parseEvents(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("监控请求参数不能为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("监控请求不是合法 JSON", exception);
        }
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw new IllegalArgumentException("监控请求必须是包含 RealtimeInf 头记录的 JSON 数组");
        }
        JsonNode header = root.get(0);
        if (!header.isObject() || !"RealtimeInf".equals(header.path("type").asText())) {
            throw new IllegalArgumentException("监控请求缺少 RealtimeInf 头记录");
        }
        if (root.size() - 1 > MAX_EVENTS) {
            throw new IllegalArgumentException("单次最多提交 " + MAX_EVENTS + " 台设备快照");
        }
        List<EquipmentTelemetryEventRequest> events = new ArrayList<>();
        for (int index = 1; index < root.size(); index++) {
            JsonNode snapshot = root.get(index);
            if (!snapshot.isObject()) {
                throw new IllegalArgumentException("第 " + index + " 项设备快照必须是对象");
            }
            events.add(toTelemetryEvent(snapshot, index));
        }
        return events;
    }

    private EquipmentTelemetryEventRequest toTelemetryEvent(JsonNode snapshot, int index) {
        String machineName = requireText(snapshot, "machinename", index, 160);
        String legacyState = requireText(snapshot, "state", index, 16);
        LocalDateTime deviceTime = parseTime(requireText(snapshot, "updateCurTime", index, 19), index);
        LegacyState state = LegacyState.from(legacyState, index);
        String machiningCountText = optionalText(snapshot, "finHole", index, 20);
        Integer machiningCount = parseNonNegativeInteger(machiningCountText, "finHole", index);
        BigDecimal progressPercent = parsePercent(optionalText(snapshot, "finper", index, 32), index);
        String programName = optionalText(snapshot, "programname", index, 160);
        String operatorName = optionalText(snapshot, "username", index, 120);
        String spindleStatus = optionalText(snapshot, "spindlestatus", index, 48);
        String screenText = optionalText(snapshot, "screentext", index, 1000);
        String runtime = optionalText(snapshot, "runtime", index, 32);
        String routeLength = optionalText(snapshot, "routLen", index, 32);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("sourceProtocol", "BTF_HESSIAN_MONITOR_V1");
        attributes.put("legacyState", legacyState.trim());
        putIfPresent(attributes, "legacyId", optionalText(snapshot, "id", index, 160));
        putIfPresent(attributes, "legacyMachineType", optionalText(snapshot, "machinetype", index, 80));
        putIfPresent(attributes, "legacyScreenText", screenText);
        if (runtime != null) {
            attributes.put("runtimeSeconds", parseNonNegativeDecimal(runtime, "runtime", index));
        }
        if (routeLength != null) {
            attributes.put("routeLength", parseNonNegativeDecimal(routeLength, "routLen", index));
        }

        return new EquipmentTelemetryEventRequest(
            null,
            machineName,
            deviceTime,
            "LEGACY_MONITOR_SNAPSHOT",
            state.qmsStatus,
            stableRecordNo(machineName, deviceTime, legacyState, machiningCountText, programName, operatorName,
                progressPercent, spindleStatus, runtime, routeLength, screenText),
            machiningCount,
            programName,
            null,
            null,
            null,
            null,
            operatorName,
            progressPercent,
            spindleStatus,
            "ALARM".equals(state.qmsStatus) && screenText != null ? "LEGACY_SCREEN_TEXT" : null,
            "ALARM".equals(state.qmsStatus) ? screenText : null,
            attributes
        );
    }

    private LocalDateTime parseTime(String value, int index) {
        try {
            return LocalDateTime.parse(value, LEGACY_TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("第 " + index + " 项 updateCurTime 格式必须为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private Integer parseNonNegativeInteger(String value, String field, int index) {
        if (value == null) return null;
        try {
            int result = Integer.parseInt(value);
            if (result < 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("第 " + index + " 项 " + field + " 必须是非负整数");
        }
    }

    private BigDecimal parsePercent(String value, int index) {
        if (value == null) return null;
        BigDecimal result = parseNonNegativeDecimal(value, "finper", index);
        if (result.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("第 " + index + " 项 finper 必须在 0 到 100 之间");
        }
        return result;
    }

    private BigDecimal parseNonNegativeDecimal(String value, String field, int index) {
        try {
            BigDecimal result = new BigDecimal(value);
            if (result.signum() < 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("第 " + index + " 项 " + field + " 必须是非负数值");
        }
    }

    private String requireText(JsonNode snapshot, String field, int index, int maximumLength) {
        String value = optionalText(snapshot, field, index, maximumLength);
        if (value == null) {
            throw new IllegalArgumentException("第 " + index + " 项缺少 " + field);
        }
        return value;
    }

    private String optionalText(JsonNode snapshot, String field, int index, int maximumLength) {
        JsonNode value = snapshot.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() && !value.isNumber()) {
            throw new IllegalArgumentException("第 " + index + " 项 " + field + " 必须是字符串或数值");
        }
        String text = value.asText().trim();
        if (text.isEmpty()) return null;
        if (text.length() > maximumLength) {
            throw new IllegalArgumentException("第 " + index + " 项 " + field + " 长度不能超过 " + maximumLength);
        }
        return text;
    }

    private void putIfPresent(Map<String, Object> attributes, String key, String value) {
        if (value != null) attributes.put(key, value);
    }

    private String stableRecordNo(String machineName, LocalDateTime deviceTime, String state, String machiningCount,
                                  String programName, String operatorName, BigDecimal progressPercent,
                                  String spindleStatus, String runtime, String routeLength, String screenText) {
        String raw = String.join("\u001f",
            machineName,
            deviceTime.format(LEGACY_TIME_FORMAT),
            state == null ? "" : state.trim(),
            valueOrEmpty(machiningCount),
            valueOrEmpty(programName),
            valueOrEmpty(operatorName),
            progressPercent == null ? "" : progressPercent.toPlainString(),
            valueOrEmpty(spindleStatus),
            valueOrEmpty(runtime),
            valueOrEmpty(routeLength),
            valueOrEmpty(screenText));
        try {
            return "LEGACY:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("缺少 SHA-256 摘要算法", exception);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private enum LegacyState {
        WORK("PROCESSING"),
        ALAM("ALARM"),
        STOP("STOPPED"),
        IDLE("IDLE"),
        WAIT("IDLE"),
        OFFLINE("IDLE");

        private final String qmsStatus;

        LegacyState(String qmsStatus) {
            this.qmsStatus = qmsStatus;
        }

        private static LegacyState from(String value, int index) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("第 " + index + " 项 state 不受支持：" + value);
            }
        }
    }
}
