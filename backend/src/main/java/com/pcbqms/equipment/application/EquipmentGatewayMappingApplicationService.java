package com.pcbqms.equipment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.EquipmentGatewayMappingResponse;
import com.pcbqms.equipment.api.SaveEquipmentGatewayMappingRequest;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentGatewayMapping;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentGatewayMappingMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EquipmentGatewayMappingApplicationService {
    private final EquipmentGatewayMappingMapper mappingMapper;
    private final EquipmentMapper equipmentMapper;
    private final EquipmentGatewayUnmatchedMessageApplicationService unmatchedMessageApplicationService;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public List<EquipmentGatewayMappingResponse> list() {
        requireGatewayMappingManagementAccess();
        return mappingMapper.selectList(Wrappers.lambdaQuery(EquipmentGatewayMapping.class)
                .eq(EquipmentGatewayMapping::getTenantCode, TenantContext.tenantCode())
                .orderByAsc(EquipmentGatewayMapping::getMachineName))
            .stream()
            .map(mapping -> {
                Equipment equipment = accessibleEquipment(mapping.getEquipmentId());
                return EquipmentGatewayMappingResponse.from(mapping, equipment);
            })
            .toList();
    }

    @Transactional
    public EquipmentGatewayMappingResponse create(SaveEquipmentGatewayMappingRequest request) {
        requireGatewayMappingManagementAccess();
        Equipment equipment = requireEquipment(request.equipmentId());
        String username = normalizeIdentifier(request.username());
        String machineName = normalizeIdentifier(request.machineName());
        if (username == null && machineName == null) {
            throw new IllegalArgumentException("至少提供 username 或 machineName 之一");
        }
        List<EquipmentGatewayMapping> conflicts = findMappings(username, machineName);
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException("该网关设备标识已经映射到 QMS 设备");
        }
        LocalDateTime now = LocalDateTime.now();
        EquipmentGatewayMapping mapping = new EquipmentGatewayMapping();
        mapping.setId(UUID.randomUUID().toString());
        mapping.setTenantCode(TenantContext.tenantCode());
        mapping.setEquipmentId(equipment.getId());
        mapping.setSourceUsername(username);
        mapping.setMachineName(machineName);
        mapping.setCreatedBy(TenantContext.actor());
        mapping.setCreatedAt(now);
        mapping.setUpdatedAt(now);
        mappingMapper.insert(mapping);
        unmatchedMessageApplicationService.removeResolvedSource(username, machineName);
        return EquipmentGatewayMappingResponse.from(mapping, equipment);
    }

    @Transactional
    public EquipmentGatewayMappingResponse update(String id, SaveEquipmentGatewayMappingRequest request) {
        requireGatewayMappingManagementAccess();
        EquipmentGatewayMapping mapping = requireMapping(id);
        Equipment equipment = requireEquipment(request.equipmentId());
        String username = normalizeIdentifier(request.username());
        String machineName = normalizeIdentifier(request.machineName());
        if (username == null && machineName == null) {
            throw new IllegalArgumentException("至少提供 username 或 machineName 之一");
        }
        if (findMappings(username, machineName).stream().anyMatch(other -> !id.equals(other.getId()))) {
            throw new IllegalArgumentException("该网关设备标识已经映射到 QMS 设备");
        }
        mapping.setEquipmentId(equipment.getId());
        mapping.setSourceUsername(username);
        mapping.setMachineName(machineName);
        mapping.setUpdatedAt(LocalDateTime.now());
        mappingMapper.updateById(mapping);
        unmatchedMessageApplicationService.removeResolvedSource(username, machineName);
        return EquipmentGatewayMappingResponse.from(mapping, equipment);
    }

    @Transactional
    public void delete(String id) {
        requireGatewayMappingManagementAccess();
        requireMapping(id);
        mappingMapper.deleteById(id);
    }

    private EquipmentGatewayMapping requireMapping(String id) {
        EquipmentGatewayMapping mapping = mappingMapper.selectById(id);
        if (mapping == null || !TenantContext.tenantCode().equals(mapping.getTenantCode())) {
            throw new IllegalArgumentException("设备网关映射不存在或无权操作");
        }
        return mapping;
    }

    /** Resolves the protocol-independent source identifiers submitted by a gateway. */
    public ResolvedEquipment resolve(String username, String machineName) {
        String normalizedUsername = normalizeIdentifier(username);
        String normalizedMachineName = normalizeIdentifier(machineName);
        if (normalizedUsername == null && normalizedMachineName == null) {
            throw new IllegalArgumentException("遥测数据缺少 username 或 machineName");
        }
        List<EquipmentGatewayMapping> matches = findMappings(normalizedUsername, normalizedMachineName);
        if (matches.isEmpty()) {
            throw new MissingEquipmentGatewayMappingException("未找到网关设备映射，请先在 QMS 配置 username 或 machineName");
        }
        Set<String> equipmentIds = new HashSet<>();
        matches.forEach(mapping -> equipmentIds.add(mapping.getEquipmentId()));
        if (equipmentIds.size() != 1) {
            throw new IllegalArgumentException("username 与 machineName 映射到不同 QMS 设备");
        }
        Equipment equipment = requireEquipment(equipmentIds.iterator().next());
        EquipmentGatewayMapping mapping = matches.getFirst();
        return new ResolvedEquipment(equipment, mapping);
    }

    private List<EquipmentGatewayMapping> findMappings(String username, String machineName) {
        return mappingMapper.selectList(Wrappers.lambdaQuery(EquipmentGatewayMapping.class)
            .eq(EquipmentGatewayMapping::getTenantCode, TenantContext.tenantCode())
            .and(query -> {
                if (username != null) {
                    query.eq(EquipmentGatewayMapping::getSourceUsername, username);
                }
                if (machineName != null) {
                    if (username != null) {
                        query.or();
                    }
                    query.eq(EquipmentGatewayMapping::getMachineName, machineName);
                }
            }));
    }

    private Equipment accessibleEquipment(String id) {
        if (!StringUtils.hasText(id)) return null;
        Equipment equipment = equipmentMapper.selectById(id);
        return equipment != null && TenantContext.tenantCode().equals(equipment.getTenantCode()) ? equipment : null;
    }

    private Equipment requireEquipment(String id) {
        Equipment equipment = accessibleEquipment(id);
        if (equipment == null) {
            throw new IllegalArgumentException("设备不存在或无权操作");
        }
        return equipment;
    }

    /**
     * A gateway mapping is equipment-operational data, not tenant security configuration.
     * Supervisors already have full equipment scope and may maintain the equipment ledger,
     * so they must also be able to connect a field device to that ledger.
     */
    private void requireGatewayMappingManagementAccess() {
        inspectionAccessScopeService.requireSupervisorOrAdministrator();
    }

    public static String normalizeIdentifier(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(java.util.Locale.ROOT) : null;
    }

    public record ResolvedEquipment(Equipment equipment, EquipmentGatewayMapping mapping) {
    }

    /** Identifies a source device that may be skipped by a protocol adapter without masking other validation errors. */
    public static class MissingEquipmentGatewayMappingException extends IllegalArgumentException {
        public MissingEquipmentGatewayMappingException(String message) {
            super(message);
        }
    }
}
