package com.pcbqms.equipment.api;

import com.pcbqms.equipment.domain.EquipmentGatewayUnmatchedMessage;
import java.time.LocalDateTime;

public record EquipmentGatewayUnmatchedMessageResponse(
    String id,
    String sourceUsername,
    String machineName,
    LocalDateTime lastDeviceTime,
    LocalDateTime lastReceivedAt,
    int occurrenceCount,
    String rawPayload
) {
    public static EquipmentGatewayUnmatchedMessageResponse from(EquipmentGatewayUnmatchedMessage message) {
        return new EquipmentGatewayUnmatchedMessageResponse(
            message.getId(), message.getSourceUsername(), message.getMachineName(), message.getLastDeviceTime(),
            message.getLastReceivedAt(), message.getOccurrenceCount(), message.getRawPayload()
        );
    }
}
