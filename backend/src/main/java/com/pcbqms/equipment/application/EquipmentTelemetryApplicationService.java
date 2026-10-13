package com.pcbqms.equipment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentTelemetryEventRequest;
import com.pcbqms.equipment.api.EquipmentTelemetryRequest;
import com.pcbqms.equipment.api.EquipmentTelemetryResponse;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentAlert;
import com.pcbqms.equipment.domain.EquipmentBusinessEvent;
import com.pcbqms.equipment.domain.EquipmentRuntimeChannel;
import com.pcbqms.equipment.domain.EquipmentRuntimeStatus;
import com.pcbqms.equipment.domain.EquipmentTelemetryRecord;
import com.pcbqms.equipment.domain.EquipmentTelemetrySummary;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentAlertMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentBusinessEventMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeChannelMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeStatusMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetryRecordMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetrySummaryMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EquipmentTelemetryApplicationService {
    private static final Set<String> SUPPORTED_STATUSES = Set.of("IDLE", "PROCESSING", "ALARM", "STOPPED", "COMPLETED");
    private final EquipmentGatewayMappingApplicationService gatewayMappingService;
    private final EquipmentTelemetryRecordMapper telemetryRecordMapper;
    private final EquipmentTelemetrySummaryMapper telemetrySummaryMapper;
    private final EquipmentRuntimeStatusMapper runtimeStatusMapper;
    private final EquipmentRuntimeChannelMapper runtimeChannelMapper;
    private final EquipmentBusinessEventMapper businessEventMapper;
    private final EquipmentAlertMapper equipmentAlertMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public EquipmentTelemetryResponse receive(EquipmentTelemetryRequest request) {
        int acceptedCount = 0;
        int duplicateCount = 0;
        for (EquipmentTelemetryEventRequest event : request.events()) {
            validateIdempotencyFields(event);
            EquipmentGatewayMappingApplicationService.ResolvedEquipment resolved;
            try {
                resolved = gatewayMappingService.resolve(event.username(), event.machineName());
            } catch (EquipmentGatewayMappingApplicationService.IgnoredEquipmentGatewayMappingException exception) {
                continue;
            }
            Equipment equipment = resolved.equipment();
            LocalDateTime now = LocalDateTime.now();
            String payload = serialize(event);
            EquipmentTelemetryRecord record = toRecord(event, equipment, now, payload);
            try {
                telemetryRecordMapper.insert(record);
            } catch (DuplicateKeyException exception) {
                if (telemetryRecordMapper.countByDeduplicationKey(
                    record.getTenantCode(), record.getEquipmentId(), record.getDeduplicationKey()) == 0) {
                    throw exception;
                }
                duplicateCount++;
                continue;
            }
            acceptedCount++;
            telemetrySummaryMapper.upsertMinute(toSummary(record, record.getDeviceTime().truncatedTo(ChronoUnit.MINUTES), now));
            telemetrySummaryMapper.upsertHour(toSummary(record, record.getDeviceTime().truncatedTo(ChronoUnit.HOURS), now));
            String sourceName = StringUtils.hasText(record.getMachineName())
                ? record.getMachineName() : record.getSourceUsername();
            EquipmentRuntimeChannel channel = runtimeChannelMapper.selectOne(Wrappers.lambdaQuery(EquipmentRuntimeChannel.class)
                .eq(EquipmentRuntimeChannel::getTenantCode, record.getTenantCode())
                .eq(EquipmentRuntimeChannel::getEquipmentId, equipment.getId())
                .eq(EquipmentRuntimeChannel::getSourceName, sourceName));
            boolean isCurrent = channel == null || !event.deviceTime().isBefore(channel.getLastDeviceTime());
            if (isCurrent) {
                triggerBusinessRules(channel == null ? null : channel.getReportedStatus(), record, event, equipment, now, payload);
            }
            saveRuntimeChannel(channel, record, sourceName, isCurrent, now);
            EquipmentRuntimeStatus previous = runtimeStatusMapper.selectOne(Wrappers.lambdaQuery(EquipmentRuntimeStatus.class)
                .eq(EquipmentRuntimeStatus::getTenantCode, TenantContext.tenantCode())
                .eq(EquipmentRuntimeStatus::getEquipmentId, equipment.getId()));
            List<EquipmentRuntimeChannel> channels = runtimeChannelMapper.selectList(Wrappers.lambdaQuery(EquipmentRuntimeChannel.class)
                .eq(EquipmentRuntimeChannel::getTenantCode, record.getTenantCode())
                .eq(EquipmentRuntimeChannel::getEquipmentId, equipment.getId()));
            saveRuntimeStatus(previous, record, channels, isCurrent, now);
        }
        return new EquipmentTelemetryResponse(acceptedCount, duplicateCount);
    }

    private void validateIdempotencyFields(EquipmentTelemetryEventRequest event) {
        if (!StringUtils.hasText(event.recordNo()) && event.machiningCount() == null) {
            throw new IllegalArgumentException("遥测数据必须提供 recordNo 或 machiningCount 以便去重");
        }
        if (event.machiningCount() != null && event.machiningCount() < 0) {
            throw new IllegalArgumentException("machiningCount 不能小于 0");
        }
    }

    private EquipmentTelemetryRecord toRecord(EquipmentTelemetryEventRequest event, Equipment equipment,
                                               LocalDateTime now, String payload) {
        EquipmentTelemetryRecord record = new EquipmentTelemetryRecord();
        record.setId(UUID.randomUUID().toString());
        record.setTenantCode(TenantContext.tenantCode());
        record.setEquipmentId(equipment.getId());
        record.setSourceUsername(EquipmentGatewayMappingApplicationService.normalizeIdentifier(event.username()));
        record.setMachineName(EquipmentGatewayMappingApplicationService.normalizeIdentifier(event.machineName()));
        record.setDeviceTime(event.deviceTime());
        record.setReceivedAt(now);
        record.setEventType(normalizeEventType(event.eventType()));
        record.setEquipmentStatus(normalizeStatus(event.status()));
        record.setProgramNo(trim(event.programNo()));
        record.setProgramVersion(trim(event.programVersion()));
        record.setMaterialNo(trim(event.materialNo()));
        record.setWorkOrderNo(trim(event.workOrderNo()));
        record.setBatchNo(trim(event.batchNo()));
        record.setOperatorName(trim(event.operatorName()));
        record.setProgressPercent(event.progressPercent());
        record.setSpindleStatus(trim(event.spindleStatus()));
        record.setAlarmCode(trim(event.alarmCode()));
        record.setAlarmMessage(trim(event.alarmMessage()));
        record.setMachiningCount(event.machiningCount());
        record.setSourceRecordNo(trim(event.recordNo()));
        String sourceName = StringUtils.hasText(record.getMachineName()) ? record.getMachineName() : record.getSourceUsername();
        record.setDeduplicationKey(equipment.getAssetNo() + "|" + sourceName + "|"
            + event.deviceTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            + "|" + (StringUtils.hasText(event.recordNo()) ? event.recordNo().trim() : "COUNT:" + event.machiningCount()));
        record.setRawPayload(payload);
        record.setCreatedAt(now);
        return record;
    }

    private EquipmentTelemetrySummary toSummary(EquipmentTelemetryRecord record, LocalDateTime bucketStart, LocalDateTime now) {
        EquipmentTelemetrySummary summary = new EquipmentTelemetrySummary();
        summary.setId(UUID.randomUUID().toString());
        summary.setTenantCode(record.getTenantCode());
        summary.setEquipmentId(record.getEquipmentId());
        summary.setBucketStart(bucketStart);
        summary.setFirstDeviceTime(record.getDeviceTime());
        summary.setLastDeviceTime(record.getDeviceTime());
        summary.setLastReceivedAt(record.getReceivedAt());
        summary.setSampleCount(1);
        summary.setLastEventType(record.getEventType());
        summary.setLastEquipmentStatus(record.getEquipmentStatus());
        summary.setLastProgramNo(record.getProgramNo());
        summary.setLastOperatorName(record.getOperatorName());
        summary.setMaxProgressPercent(record.getProgressPercent());
        summary.setLastSpindleStatus(record.getSpindleStatus());
        summary.setLastAlarmCode(record.getAlarmCode());
        summary.setLastAlarmMessage(record.getAlarmMessage());
        summary.setLastMachiningCount(record.getMachiningCount());
        summary.setMaxMachiningCount(record.getMachiningCount());
        summary.setCreatedAt(now);
        summary.setUpdatedAt(now);
        return summary;
    }

    private void saveRuntimeChannel(EquipmentRuntimeChannel previous, EquipmentTelemetryRecord record,
                                    String sourceName, boolean isCurrent, LocalDateTime now) {
        boolean isNew = previous == null;
        EquipmentRuntimeChannel channel = isNew ? new EquipmentRuntimeChannel() : previous;
        if (isNew) {
            channel.setId(UUID.randomUUID().toString());
            channel.setTenantCode(record.getTenantCode());
            channel.setEquipmentId(record.getEquipmentId());
            channel.setSourceName(sourceName);
        }
        if (isCurrent) {
            if (record.getEquipmentStatus() != null) channel.setReportedStatus(record.getEquipmentStatus());
            setIfPresent(record.getProgramNo(), channel::setProgramNo);
            setIfPresent(record.getOperatorName(), channel::setOperatorName);
            if (record.getProgressPercent() != null) channel.setProgressPercent(record.getProgressPercent());
            setIfPresent(record.getSpindleStatus(), channel::setSpindleStatus);
            if ("ALARM".equals(record.getEquipmentStatus())) {
                channel.setAlarmCode(record.getAlarmCode());
                channel.setAlarmMessage(record.getAlarmMessage());
            } else if (record.getEquipmentStatus() != null) {
                channel.setAlarmCode(null);
                channel.setAlarmMessage(null);
            }
            if (record.getMachiningCount() != null) channel.setMachiningCount(record.getMachiningCount());
            channel.setLastDeviceTime(record.getDeviceTime());
        }
        channel.setLastReceivedAt(now);
        channel.setUpdatedAt(now);
        if (isNew) runtimeChannelMapper.insert(channel);
        else runtimeChannelMapper.updateById(channel);
    }

    private void saveRuntimeStatus(EquipmentRuntimeStatus previous, EquipmentTelemetryRecord record,
                                   List<EquipmentRuntimeChannel> channels, boolean isCurrent, LocalDateTime now) {
        boolean isNew = previous == null;
        EquipmentRuntimeStatus status = isNew ? new EquipmentRuntimeStatus() : previous;
        if (isNew) {
            status.setId(UUID.randomUUID().toString());
            status.setTenantCode(record.getTenantCode());
            status.setEquipmentId(record.getEquipmentId());
        }
        EquipmentRuntimeChannel latest = channels.stream()
            .max(Comparator.comparing(EquipmentRuntimeChannel::getLastDeviceTime)).orElseThrow();
        EquipmentRuntimeChannel alarming = channels.stream()
            .filter(item -> "ALARM".equals(item.getReportedStatus())).findFirst().orElse(null);
        status.setReportedStatus(channels.stream().map(EquipmentRuntimeChannel::getReportedStatus).filter(Objects::nonNull)
            .min(Comparator.comparingInt(EquipmentTelemetryApplicationService::statusPriority)).orElse(null));
        status.setAlarmCode(alarming == null ? null : alarming.getAlarmCode());
        status.setAlarmMessage(alarming == null ? null : alarming.getAlarmMessage());
        if (channels.size() == 1) {
            if (isCurrent) {
                setIfPresent(record.getProgramNo(), status::setProgramNo);
                setIfPresent(record.getProgramVersion(), status::setProgramVersion);
                setIfPresent(record.getMaterialNo(), status::setMaterialNo);
                setIfPresent(record.getWorkOrderNo(), status::setWorkOrderNo);
                setIfPresent(record.getBatchNo(), status::setBatchNo);
                setIfPresent(record.getOperatorName(), status::setOperatorName);
                if (record.getProgressPercent() != null) status.setProgressPercent(record.getProgressPercent());
                setIfPresent(record.getSpindleStatus(), status::setSpindleStatus);
                if (record.getMachiningCount() != null) status.setMachiningCount(record.getMachiningCount());
                setIfPresent(record.getSourceRecordNo(), status::setLastSourceRecordNo);
            }
        } else {
            // There is no single current program or progress for a multi-program machine.
            status.setProgramNo(null);
            status.setProgramVersion(null);
            status.setMaterialNo(null);
            status.setWorkOrderNo(null);
            status.setBatchNo(null);
            status.setOperatorName(null);
            status.setProgressPercent(null);
            status.setSpindleStatus(null);
            status.setMachiningCount(null);
            status.setLastSourceRecordNo(null);
        }
        status.setLastDeviceTime(latest.getLastDeviceTime());
        status.setLastReceivedAt(channels.stream().map(EquipmentRuntimeChannel::getLastReceivedAt)
            .max(LocalDateTime::compareTo).orElse(now));
        status.setUpdatedAt(now);
        if (isNew) {
            runtimeStatusMapper.insert(status);
        } else {
            runtimeStatusMapper.updateById(status);
        }
    }

    private void triggerBusinessRules(String priorStatus, EquipmentTelemetryRecord record,
                                      EquipmentTelemetryEventRequest request, Equipment equipment,
                                      LocalDateTime now, String payload) {
        String currentStatus = record.getEquipmentStatus();
        if ("ALARM".equals(currentStatus) && !"ALARM".equals(priorStatus)) {
            createBusinessEvent(record, equipment, "ALARM_RAISED", now, payload);
            createEquipmentAlarm(record, equipment, now);
        }
        if ("STOPPED".equals(currentStatus) && !"STOPPED".equals(priorStatus)) {
            createBusinessEvent(record, equipment, "MACHINE_STOPPED", now, payload);
        }
        boolean completed = "COMPLETED".equals(currentStatus)
            || "MACHINING_COMPLETED".equals(normalizeEventType(request.eventType()));
        if (completed && (!"COMPLETED".equals(priorStatus) || "MACHINING_COMPLETED".equals(normalizeEventType(request.eventType())))) {
            createBusinessEvent(record, equipment, "MACHINING_COMPLETED", now, payload);
        }
    }

    private static int statusPriority(String value) {
        return switch (value) {
            case "ALARM" -> 0;
            case "PROCESSING" -> 1;
            case "STOPPED" -> 2;
            case "IDLE" -> 3;
            case "COMPLETED" -> 4;
            default -> 5;
        };
    }

    private void createBusinessEvent(EquipmentTelemetryRecord record, Equipment equipment, String eventType,
                                     LocalDateTime now, String payload) {
        EquipmentBusinessEvent event = new EquipmentBusinessEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTenantCode(TenantContext.tenantCode());
        event.setEquipmentId(equipment.getId());
        event.setTelemetryId(record.getId());
        event.setEventType(eventType);
        event.setStatus("PENDING");
        event.setPayload(payload);
        event.setCreatedAt(now);
        businessEventMapper.insertWithPayload(event);
    }

    private void createEquipmentAlarm(EquipmentTelemetryRecord record, Equipment equipment, LocalDateTime now) {
        EquipmentAlert alert = new EquipmentAlert();
        alert.setId(UUID.randomUUID().toString());
        alert.setTenantCode(TenantContext.tenantCode());
        alert.setAlertNo("MA-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-"
            + alert.getId().substring(0, 8).toUpperCase(Locale.ROOT));
        alert.setEquipmentId(equipment.getId());
        alert.setMaintenanceId(null);
        alert.setTitle(equipment.getName() + "设备报警");
        alert.setDescription((StringUtils.hasText(record.getAlarmCode()) ? "[" + record.getAlarmCode() + "] " : "")
            + (StringUtils.hasText(record.getAlarmMessage()) ? record.getAlarmMessage() : "现场网关上报设备报警"));
        alert.setStatus("OPEN");
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        equipmentAlertMapper.insert(alert);
    }

    private String serialize(EquipmentTelemetryEventRequest event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("遥测数据无法序列化", exception);
        }
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT)
            .replace("RUNNING", "PROCESSING")
            .replace("RUN", "PROCESSING")
            .replace("FINISHED", "COMPLETED");
        if (!SUPPORTED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("不支持的设备状态：" + value);
        }
        return normalized;
    }

    private String normalizeEventType(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) setter.accept(value);
    }
}
