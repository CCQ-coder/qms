package com.pcbqms.equipment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentRuntimeStatusResponse;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentRuntimeStatus;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentRuntimeStatusMapper;
import com.pcbqms.inspection.domain.SystemParameter;
import com.pcbqms.inspection.infrastructure.persistence.SystemParameterMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EquipmentRuntimeStatusApplicationService {
    private static final String OFFLINE_AFTER_SECONDS_KEY = "equipment.monitor.offline-after-seconds";
    private static final long MIN_OFFLINE_AFTER_SECONDS = 30;
    private static final long MAX_OFFLINE_AFTER_SECONDS = 86_400;
    private final EquipmentRuntimeStatusMapper runtimeStatusMapper;
    private final EquipmentMapper equipmentMapper;
    private final SystemParameterMapper systemParameterMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;
    @Value("${qms.equipment-telemetry.offline-after-seconds:300}")
    private long offlineAfterSeconds;

    public List<EquipmentRuntimeStatusResponse> list() {
        var access = inspectionAccessScopeService.currentAccess();
        LocalDateTime now = LocalDateTime.now();
        return runtimeStatusMapper.selectList(Wrappers.lambdaQuery(EquipmentRuntimeStatus.class)
                .eq(EquipmentRuntimeStatus::getTenantCode, TenantContext.tenantCode())
                .orderByAsc(EquipmentRuntimeStatus::getEquipmentId))
            .stream()
            .map(status -> toResponse(status, access, now))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private EquipmentRuntimeStatusResponse toResponse(EquipmentRuntimeStatus status,
                                                       InspectionAccessScopeService.InspectionAccess access,
                                                       LocalDateTime now) {
        Equipment equipment = equipmentMapper.selectById(status.getEquipmentId());
        if (equipment == null || !TenantContext.tenantCode().equals(equipment.getTenantCode())
            || (!access.fullDataAccess() && (!StringUtils.hasText(equipment.getProcessCode())
                || !access.canAccess(equipment.getProcessCode())))) {
            return null;
        }
        boolean online = status.getLastReceivedAt() != null
            && Duration.between(status.getLastReceivedAt(), now).getSeconds() < resolveOfflineAfterSeconds();
        return EquipmentRuntimeStatusResponse.from(equipment, status, online);
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
