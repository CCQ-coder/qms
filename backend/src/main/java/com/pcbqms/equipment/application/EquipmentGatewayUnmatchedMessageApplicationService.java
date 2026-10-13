package com.pcbqms.equipment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentGatewayUnmatchedMessageResponse;
import com.pcbqms.equipment.domain.EquipmentGatewayUnmatchedMessage;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentGatewayUnmatchedMessageMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EquipmentGatewayUnmatchedMessageApplicationService {
    private final EquipmentGatewayUnmatchedMessageMapper unmatchedMessageMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    /** Records the latest packet so an administrator can configure an otherwise invisible field source. */
    public void record(String username, String machineName, LocalDateTime deviceTime, String rawPayload) {
        String normalizedMachineName = EquipmentGatewayMappingApplicationService.normalizeIdentifier(machineName);
        String normalizedUsername = EquipmentGatewayMappingApplicationService.normalizeIdentifier(username);
        String sourceKey = normalizedMachineName != null ? "MACHINE:" + normalizedMachineName : "USER:" + normalizedUsername;
        LocalDateTime now = LocalDateTime.now();
        EquipmentGatewayUnmatchedMessage existing = unmatchedMessageMapper.selectOne(
            Wrappers.lambdaQuery(EquipmentGatewayUnmatchedMessage.class)
                .eq(EquipmentGatewayUnmatchedMessage::getTenantCode, TenantContext.tenantCode())
                .eq(EquipmentGatewayUnmatchedMessage::getSourceKey, sourceKey));
        if (existing == null) {
            existing = new EquipmentGatewayUnmatchedMessage();
            existing.setId(UUID.randomUUID().toString());
            existing.setTenantCode(TenantContext.tenantCode());
            existing.setSourceKey(sourceKey);
            existing.setSourceUsername(normalizedUsername);
            existing.setMachineName(normalizedMachineName);
            existing.setCreatedAt(now);
            existing.setOccurrenceCount(0);
            existing.setLastDeviceTime(deviceTime);
            existing.setLastReceivedAt(now);
            existing.setRawPayload(rawPayload);
            existing.setUpdatedAt(now);
            existing.setOccurrenceCount(1);
            unmatchedMessageMapper.insert(existing);
            return;
        }
        existing.setSourceUsername(normalizedUsername);
        existing.setMachineName(normalizedMachineName);
        existing.setLastDeviceTime(deviceTime);
        existing.setLastReceivedAt(now);
        existing.setRawPayload(rawPayload);
        existing.setUpdatedAt(now);
        existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
        unmatchedMessageMapper.updateById(existing);
    }

    public List<EquipmentGatewayUnmatchedMessageResponse> list() {
        inspectionAccessScopeService.requireSupervisorOrAdministrator();
        return unmatchedMessageMapper.selectList(Wrappers.lambdaQuery(EquipmentGatewayUnmatchedMessage.class)
                .eq(EquipmentGatewayUnmatchedMessage::getTenantCode, TenantContext.tenantCode())
                .orderByDesc(EquipmentGatewayUnmatchedMessage::getLastReceivedAt))
            .stream().map(EquipmentGatewayUnmatchedMessageResponse::from).toList();
    }

    public void removeResolvedSource(String username, String machineName) {
        String normalizedMachineName = EquipmentGatewayMappingApplicationService.normalizeIdentifier(machineName);
        String normalizedUsername = EquipmentGatewayMappingApplicationService.normalizeIdentifier(username);
        if (normalizedMachineName != null) {
            remove("MACHINE:" + normalizedMachineName);
        }
        if (normalizedUsername != null) {
            remove("USER:" + normalizedUsername);
        }
    }

    private void remove(String sourceKey) {
        unmatchedMessageMapper.delete(Wrappers.lambdaQuery(EquipmentGatewayUnmatchedMessage.class)
            .eq(EquipmentGatewayUnmatchedMessage::getTenantCode, TenantContext.tenantCode())
            .eq(EquipmentGatewayUnmatchedMessage::getSourceKey, sourceKey));
    }
}
