package com.pcbqms.equipment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.CncFileRequest;
import com.pcbqms.equipment.api.CncFileRequestResponse;
import com.pcbqms.equipment.api.CncProgramReleaseResponse;
import com.pcbqms.equipment.api.SaveCncProgramReleaseRequest;
import com.pcbqms.equipment.domain.CncProgramRelease;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.infrastructure.persistence.CncProgramReleaseMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CncProgramReleaseApplicationService {
    private final CncProgramReleaseMapper programReleaseMapper;
    private final EquipmentGatewayMappingApplicationService gatewayMappingService;
    private final EquipmentMapper equipmentMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public List<CncProgramReleaseResponse> list() {
        inspectionAccessScopeService.requireTenantAdministrator();
        return programReleaseMapper.selectList(Wrappers.lambdaQuery(CncProgramRelease.class)
                .eq(CncProgramRelease::getTenantCode, TenantContext.tenantCode())
                .orderByAsc(CncProgramRelease::getBarcode))
            .stream().map(CncProgramReleaseResponse::from).toList();
    }

    @Transactional
    public CncProgramReleaseResponse save(SaveCncProgramReleaseRequest request) {
        inspectionAccessScopeService.requireTenantAdministrator();
        var resolved = requireQmsEquipment(request.equipmentId());
        String barcode = normalizeBarcode(request.barcode());
        CncProgramRelease release = programReleaseMapper.selectOne(Wrappers.lambdaQuery(CncProgramRelease.class)
            .eq(CncProgramRelease::getTenantCode, TenantContext.tenantCode())
            .eq(CncProgramRelease::getEquipmentId, resolved.getId())
            .eq(CncProgramRelease::getBarcode, barcode));
        LocalDateTime now = LocalDateTime.now();
        boolean isNew = release == null;
        if (isNew) {
            release = new CncProgramRelease();
            release.setId(UUID.randomUUID().toString());
            release.setTenantCode(TenantContext.tenantCode());
            release.setEquipmentId(resolved.getId());
            release.setBarcode(barcode);
            release.setCreatedBy(TenantContext.actor());
            release.setCreatedAt(now);
        }
        release.setProgramNo(request.programNo().trim());
        release.setProgramName(trim(request.programName()));
        release.setProgramVersion(request.programVersion().trim());
        release.setFileUrl(request.fileUrl().trim());
        release.setChecksumSha256(request.checksumSha256().trim().toLowerCase(Locale.ROOT));
        release.setFileSizeBytes(request.fileSizeBytes());
        release.setStatus(normalizeReleaseStatus(request.status()));
        release.setUpdatedAt(now);
        if (isNew) {
            programReleaseMapper.insert(release);
        } else {
            programReleaseMapper.updateById(release);
        }
        return CncProgramReleaseResponse.from(release);
    }

    /** This method is gateway-facing.  It only returns an explicitly ACTIVE release for the mapped device. */
    public CncFileRequestResponse requestFile(CncFileRequest request) {
        var resolved = gatewayMappingService.resolve(request.username(), request.machineName());
        Equipment equipment = resolved.equipment();
        CncProgramRelease release = programReleaseMapper.selectOne(Wrappers.lambdaQuery(CncProgramRelease.class)
            .eq(CncProgramRelease::getTenantCode, TenantContext.tenantCode())
            .eq(CncProgramRelease::getEquipmentId, equipment.getId())
            .eq(CncProgramRelease::getBarcode, normalizeBarcode(request.barcode()))
            .eq(CncProgramRelease::getStatus, "ACTIVE"));
        if (release == null) {
            return CncFileRequestResponse.rejected(equipment.getAssetNo(), "该条码未配置可下发的有效程序");
        }
        return new CncFileRequestResponse(true, "已批准", equipment.getAssetNo(), release.getProgramNo(),
            release.getProgramName(), release.getProgramVersion(), release.getFileUrl(), release.getChecksumSha256(),
            release.getFileSizeBytes());
    }

    private Equipment requireQmsEquipment(String equipmentId) {
        Equipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null || !TenantContext.tenantCode().equals(equipment.getTenantCode())) {
            throw new IllegalArgumentException("设备不存在或无权操作");
        }
        return equipment;
    }

    private String normalizeBarcode(String barcode) {
        return barcode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeReleaseStatus(String status) {
        if (!StringUtils.hasText(status)) return "ACTIVE";
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"INACTIVE".equals(normalized)) {
            throw new IllegalArgumentException("程序发布状态仅支持 ACTIVE 或 INACTIVE");
        }
        return normalized;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
