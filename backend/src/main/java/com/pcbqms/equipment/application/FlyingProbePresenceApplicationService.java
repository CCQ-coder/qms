package com.pcbqms.equipment.application;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.FlyingProbePresenceRequest;
import com.pcbqms.equipment.api.FlyingProbePresenceResponse;
import com.pcbqms.equipment.domain.EquipmentRuntimeChannel;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeChannelMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlyingProbePresenceApplicationService {
    public static final String NETWORK_SOURCE = "__FPT_NETWORK__";
    public static final String DIRECTORY_SOURCE = "__FPT_SOURCE__";
    public static final String TXT_SOURCE = "__FPT_TXT_ACTIVITY__";
    public static final LocalDateTime IDENTITY_MARKER_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Set<String> NETWORK_STATES = Set.of("ONLINE", "OFFLINE", "UNKNOWN");
    private static final Set<String> DIRECTORY_STATES = Set.of("READABLE", "UNREADABLE", "UNKNOWN");

    private final EquipmentGatewayMappingApplicationService mappingService;
    private final EquipmentRuntimeChannelMapper channelMapper;
    private final Clock clock;

    @Autowired
    public FlyingProbePresenceApplicationService(EquipmentGatewayMappingApplicationService mappingService,
                                                 EquipmentRuntimeChannelMapper channelMapper) {
        this(mappingService, channelMapper, Clock.systemUTC());
    }

    FlyingProbePresenceApplicationService(EquipmentGatewayMappingApplicationService mappingService,
                                          EquipmentRuntimeChannelMapper channelMapper, Clock clock) {
        this.mappingService = mappingService;
        this.channelMapper = channelMapper;
        this.clock = clock;
    }

    @Transactional
    public FlyingProbePresenceResponse receive(FlyingProbePresenceRequest request) {
        if (request == null || request.observedAt() == null || request.machineName() == null) {
            throw new IllegalArgumentException("飞针机状态缺少设备名或探测时间");
        }
        String networkState = normalizeState(request.networkState(), NETWORK_STATES);
        String sourceState = normalizeState(request.sourceState(), DIRECTORY_STATES);
        var nowInstant = clock.instant();
        if (Duration.between(nowInstant, request.observedAt().toInstant()).abs().compareTo(MAX_CLOCK_SKEW) > 0) {
            throw new IllegalArgumentException("飞针机探测时间与服务器时间相差超过 5 分钟");
        }
        if (request.lastTxtChangeAt() != null && request.lastTxtChangeAt().toInstant().isAfter(request.observedAt().toInstant())) {
            throw new IllegalArgumentException("TXT 变化时间不能晚于探测时间");
        }
        var resolved = mappingService.resolve(null, request.machineName());
        String equipmentId = resolved.equipment().getId();
        LocalDateTime observed = toShanghai(request.observedAt());
        LocalDateTime received = LocalDateTime.ofInstant(nowInstant, SHANGHAI);
        int accepted = channelMapper.upsertIfNewer(channel(equipmentId, NETWORK_SOURCE, networkState, observed, received));
        if (accepted == 0) return new FlyingProbePresenceResponse(false, equipmentId);
        channelMapper.upsertIfNewer(channel(equipmentId, DIRECTORY_SOURCE, sourceState, observed, received));
        if (request.lastTxtChangeAt() != null) {
            channelMapper.upsertIfNewer(channel(equipmentId, TXT_SOURCE, "CHANGED",
                toShanghai(request.lastTxtChangeAt()), received));
        }
        return new FlyingProbePresenceResponse(true, equipmentId);
    }

    private EquipmentRuntimeChannel channel(String equipmentId, String source, String state,
                                            LocalDateTime observed, LocalDateTime received) {
        EquipmentRuntimeChannel channel = new EquipmentRuntimeChannel();
        channel.setId(UUID.randomUUID().toString());
        channel.setTenantCode(TenantContext.tenantCode());
        channel.setEquipmentId(equipmentId);
        channel.setSourceName(source);
        channel.setReportedStatus(state);
        channel.setLastDeviceTime(observed);
        channel.setLastReceivedAt(received);
        channel.setUpdatedAt(received);
        return channel;
    }

    /** A durable identity hint only. INSERT IGNORE must never replace a real probe result. */
    static EquipmentRuntimeChannel identityMarker(String tenantCode, String equipmentId, LocalDateTime now) {
        EquipmentRuntimeChannel marker = new EquipmentRuntimeChannel();
        marker.setId(UUID.randomUUID().toString());
        marker.setTenantCode(tenantCode);
        marker.setEquipmentId(equipmentId);
        marker.setSourceName(NETWORK_SOURCE);
        marker.setReportedStatus("UNKNOWN");
        marker.setLastDeviceTime(IDENTITY_MARKER_TIME);
        marker.setLastReceivedAt(IDENTITY_MARKER_TIME);
        marker.setUpdatedAt(now);
        return marker;
    }

    private String normalizeState(String value, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("无效的飞针机状态");
        return normalized;
    }

    private LocalDateTime toShanghai(OffsetDateTime value) {
        return LocalDateTime.ofInstant(value.toInstant(), SHANGHAI);
    }
}
