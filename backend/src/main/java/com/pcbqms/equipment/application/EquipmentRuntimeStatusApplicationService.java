package com.pcbqms.equipment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentRuntimeStatusResponse;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentRuntimeChannel;
import com.pcbqms.equipment.domain.EquipmentRuntimeStatus;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeChannelMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeStatusMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentTelemetryRecordMapper;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EquipmentRuntimeStatusApplicationService {
    private static final String OFFLINE_AFTER_SECONDS_KEY = "equipment.monitor.offline-after-seconds";
    private static final long MIN_OFFLINE_AFTER_SECONDS = 30;
    private static final long MAX_OFFLINE_AFTER_SECONDS = 86_400;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final long HISTORICAL_IDS_CACHE_SECONDS = 600;

    private final EquipmentRuntimeStatusMapper runtimeStatusMapper;
    private final EquipmentMapper equipmentMapper;
    private final SystemParameterMapper systemParameterMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;
    private final EquipmentRuntimeChannelMapper channelMapper;
    private final EquipmentTelemetryRecordMapper telemetryRecordMapper;
    private final FlyingProbePresenceStatusResolver presenceResolver = new FlyingProbePresenceStatusResolver();
    private final Clock clock;
    private final Map<String, HistoricalIds> historicalIdsCache = new ConcurrentHashMap<>();
    @Value("${qms.equipment-telemetry.offline-after-seconds:300}")
    private long offlineAfterSeconds = 300;
    @Value("${qms.flying-probe.presence-ttl-seconds:300}")
    private long presenceTtlSeconds = 300;
    @Value("${qms.flying-probe.activity-window-seconds:600}")
    private long activityWindowSeconds = 600;

    @Autowired
    public EquipmentRuntimeStatusApplicationService(EquipmentRuntimeStatusMapper runtimeStatusMapper,
        EquipmentMapper equipmentMapper, SystemParameterMapper systemParameterMapper,
        InspectionAccessScopeService inspectionAccessScopeService, EquipmentRuntimeChannelMapper channelMapper,
        EquipmentTelemetryRecordMapper telemetryRecordMapper) {
        this(runtimeStatusMapper, equipmentMapper, systemParameterMapper, inspectionAccessScopeService,
            channelMapper, telemetryRecordMapper, Clock.systemUTC());
    }

    EquipmentRuntimeStatusApplicationService(EquipmentRuntimeStatusMapper runtimeStatusMapper,
        EquipmentMapper equipmentMapper, SystemParameterMapper systemParameterMapper,
        InspectionAccessScopeService inspectionAccessScopeService, EquipmentRuntimeChannelMapper channelMapper,
        EquipmentTelemetryRecordMapper telemetryRecordMapper, Clock clock) {
        this.runtimeStatusMapper = runtimeStatusMapper;
        this.equipmentMapper = equipmentMapper;
        this.systemParameterMapper = systemParameterMapper;
        this.inspectionAccessScopeService = inspectionAccessScopeService;
        this.channelMapper = channelMapper;
        this.telemetryRecordMapper = telemetryRecordMapper;
        this.clock = clock;
    }

    public List<EquipmentRuntimeStatusResponse> list() {
        String tenantCode = TenantContext.tenantCode();
        var access = inspectionAccessScopeService.currentAccess();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
        LocalDateTime ordinaryNow = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
        List<EquipmentRuntimeStatus> statuses = runtimeStatusMapper.selectList(
            Wrappers.lambdaQuery(EquipmentRuntimeStatus.class)
                .eq(EquipmentRuntimeStatus::getTenantCode, tenantCode)
                .orderByAsc(EquipmentRuntimeStatus::getEquipmentId));
        Map<String, EquipmentRuntimeStatus> runtimeById = new HashMap<>();
        statuses.forEach(status -> runtimeById.put(status.getEquipmentId(), status));

        Map<String, Map<String, EquipmentRuntimeChannel>> channelsById = new HashMap<>();
        for (EquipmentRuntimeChannel channel : channelMapper.selectReservedByTenant(tenantCode)) {
            channelsById.computeIfAbsent(channel.getEquipmentId(), ignored -> new HashMap<>())
                .put(channel.getSourceName(), channel);
        }
        Set<String> historicalFlyingProbeIds = historicalFlyingProbeIds(tenantCode);
        Set<String> allEquipmentIds = new TreeSet<>(runtimeById.keySet());
        allEquipmentIds.addAll(channelsById.keySet());
        allEquipmentIds.addAll(historicalFlyingProbeIds);
        List<EquipmentRuntimeStatusResponse> responses = new ArrayList<>();
        Long ordinaryOfflineAfter = null;
        for (String equipmentId : allEquipmentIds) {
            Equipment equipment = equipmentMapper.selectById(equipmentId);
            if (!accessible(equipment, tenantCode, access)) continue;
            EquipmentRuntimeStatus runtime = runtimeById.get(equipmentId);
            Map<String, EquipmentRuntimeChannel> channels = channelsById.get(equipmentId);
            if (channels != null || historicalFlyingProbeIds.contains(equipmentId)) {
                EquipmentRuntimeChannel network = channels == null ? null : channels.get(FlyingProbePresenceApplicationService.NETWORK_SOURCE);
                EquipmentRuntimeChannel source = channels == null ? null : channels.get(FlyingProbePresenceApplicationService.DIRECTORY_SOURCE);
                EquipmentRuntimeChannel txt = channels == null ? null : channels.get(FlyingProbePresenceApplicationService.TXT_SOURCE);
                var decision = presenceResolver.resolve(network, source, txt, now,
                    presenceTtlSeconds, activityWindowSeconds);
                responses.add(EquipmentRuntimeStatusResponse.fromPresence(equipment, runtime,
                    network == null ? "UNKNOWN" : network.getReportedStatus(), decision.effectiveStatus(),
                    decision.online(), decision.lastProbeAt(), decision.lastTxtChangeAt()));
            } else if (runtime != null) {
                if (ordinaryOfflineAfter == null) ordinaryOfflineAfter = resolveOfflineAfterSeconds();
                boolean online = runtime.getLastReceivedAt() != null
                    && Duration.between(runtime.getLastReceivedAt(), ordinaryNow).getSeconds() < ordinaryOfflineAfter;
                responses.add(EquipmentRuntimeStatusResponse.from(equipment, runtime, online));
            }
        }
        return responses;
    }

    private boolean accessible(Equipment equipment, String tenantCode,
                               InspectionAccessScopeService.InspectionAccess access) {
        return equipment != null && tenantCode.equals(equipment.getTenantCode())
            && (access.fullDataAccess() || (StringUtils.hasText(equipment.getProcessCode())
                && access.canAccess(equipment.getProcessCode())));
    }

    private Set<String> historicalFlyingProbeIds(String tenantCode) {
        Instant now = clock.instant();
        HistoricalIds cached = historicalIdsCache.compute(tenantCode, (ignored, current) -> {
            if (current != null && current.expiresAt().isAfter(now)) return current;
            List<String> found = telemetryRecordMapper.selectFlyingProbeEquipmentIds(tenantCode);
            LocalDateTime markedAt = LocalDateTime.ofInstant(now, SHANGHAI);
            found.forEach(equipmentId -> channelMapper.insertIfAbsent(
                FlyingProbePresenceApplicationService.identityMarker(tenantCode, equipmentId, markedAt)));
            return new HistoricalIds(new HashSet<>(found), now.plusSeconds(HISTORICAL_IDS_CACHE_SECONDS));
        });
        return cached.ids();
    }

    /** Keep an already loaded historical-id cache current as new test data arrives. */
    public void markFlyingProbeTelemetry(String tenantCode, String equipmentId) {
        historicalIdsCache.computeIfPresent(tenantCode, (ignored, current) -> {
            Set<String> ids = new HashSet<>(current.ids());
            ids.add(equipmentId);
            return new HistoricalIds(ids, current.expiresAt());
        });
    }

    private record HistoricalIds(Set<String> ids, Instant expiresAt) {
    }

    /** Tenant administrators may adjust this operational threshold without redeploying the QMS service. */
    private long resolveOfflineAfterSeconds() {
        SystemParameter parameter = systemParameterMapper.selectOne(Wrappers.lambdaQuery(SystemParameter.class)
            .eq(SystemParameter::getTenantCode, TenantContext.tenantCode())
            .eq(SystemParameter::getParameterKey, OFFLINE_AFTER_SECONDS_KEY));
        if (parameter == null || !StringUtils.hasText(parameter.getParameterValue())) {
            return Math.min(MAX_OFFLINE_AFTER_SECONDS, Math.max(MIN_OFFLINE_AFTER_SECONDS, offlineAfterSeconds));
        }
        try {
            long configured = Long.parseLong(parameter.getParameterValue().trim());
            return Math.min(MAX_OFFLINE_AFTER_SECONDS, Math.max(MIN_OFFLINE_AFTER_SECONDS, configured));
        } catch (NumberFormatException ignored) {
            return Math.min(MAX_OFFLINE_AFTER_SECONDS, Math.max(MIN_OFFLINE_AFTER_SECONDS, offlineAfterSeconds));
        }
    }
}
