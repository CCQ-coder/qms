package com.pcbqms.equipment.application;

import com.pcbqms.equipment.domain.EquipmentRuntimeChannel;
import java.time.Duration;
import java.time.LocalDateTime;

/** Applies link, source and activity freshness in that order. */
public class FlyingProbePresenceStatusResolver {
    public PresenceDecision resolve(EquipmentRuntimeChannel network, EquipmentRuntimeChannel source,
                                    EquipmentRuntimeChannel txt, LocalDateTime now,
                                    long ttlSeconds, long activityWindowSeconds) {
        LocalDateTime probeAt = network == null || FlyingProbePresenceApplicationService.IDENTITY_MARKER_TIME
            .equals(network.getLastDeviceTime()) ? null : network.getLastDeviceTime();
        LocalDateTime txtAt = txt == null ? null : txt.getLastDeviceTime();
        if (!fresh(network, now, ttlSeconds) || !fresh(source, now, ttlSeconds)
            || !"READABLE".equals(source.getReportedStatus())
            || network.getReportedStatus() == null) {
            return new PresenceDecision("UNKNOWN", false, probeAt, txtAt);
        }
        return switch (network.getReportedStatus()) {
            case "OFFLINE" -> new PresenceDecision("OFFLINE", false, probeAt, txtAt);
            case "ONLINE" -> {
                boolean active = txtAt != null && !txtAt.isAfter(now)
                    && Duration.between(txtAt, now).compareTo(Duration.ofSeconds(activityWindowSeconds)) < 0;
                yield new PresenceDecision(active ? "RUNNING" : "ONLINE", true, probeAt, txtAt);
            }
            default -> new PresenceDecision("UNKNOWN", false, probeAt, txtAt);
        };
    }

    private boolean fresh(EquipmentRuntimeChannel channel, LocalDateTime now, long ttlSeconds) {
        return channel != null && channel.getLastReceivedAt() != null
            && !channel.getLastReceivedAt().isAfter(now)
            && Duration.between(channel.getLastReceivedAt(), now).compareTo(Duration.ofSeconds(ttlSeconds)) < 0;
    }

    public record PresenceDecision(String effectiveStatus, boolean online, LocalDateTime lastProbeAt,
                                   LocalDateTime lastTxtChangeAt) {
    }
}
