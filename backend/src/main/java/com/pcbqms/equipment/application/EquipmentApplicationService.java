package com.pcbqms.equipment.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.equipment.api.CompleteMaintenanceRequest;
import com.pcbqms.equipment.api.CreateEquipmentRequest;
import com.pcbqms.equipment.api.CreateMaintenanceRequest;
import com.pcbqms.equipment.api.EquipmentAlertResponse;
import com.pcbqms.equipment.api.EquipmentMaintenanceResponse;
import com.pcbqms.equipment.api.EquipmentPageResponse;
import com.pcbqms.equipment.api.EquipmentQueryRequest;
import com.pcbqms.equipment.api.EquipmentResponse;
import com.pcbqms.equipment.api.EquipmentTypeResponse;
import com.pcbqms.equipment.api.MaintenanceItemResponse;
import com.pcbqms.equipment.api.MaintenanceTemplateItemResponse;
import com.pcbqms.equipment.api.SaveMaintenanceTemplateItemRequest;
import com.pcbqms.equipment.api.UpdateEquipmentRequest;
import com.pcbqms.equipment.domain.Equipment;
import com.pcbqms.equipment.domain.EquipmentAlert;
import com.pcbqms.equipment.domain.EquipmentMaintenance;
import com.pcbqms.equipment.domain.EquipmentMaintenanceItem;
import com.pcbqms.equipment.domain.EquipmentMaintenanceTemplateItem;
import com.pcbqms.equipment.domain.EquipmentType;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentAlertMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMaintenanceItemMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMaintenanceMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMaintenanceTemplateItemMapper;
import com.pcbqms.equipment.infrastructure.persistence.EquipmentMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EquipmentApplicationService {
    private static final Set<String> MAINTENANCE_TYPES = Set.of("INSPECTION", "MAINTENANCE", "REPAIR", "CALIBRATION");
    private static final Pattern ASSET_NUMBER_SUFFIX = Pattern.compile("^(.*?)(\\d+)$");
    private final EquipmentMapper equipmentMapper;
    private final EquipmentMaintenanceMapper maintenanceMapper;
    private final EquipmentMaintenanceTemplateItemMapper maintenanceTemplateItemMapper;
    private final EquipmentMaintenanceItemMapper maintenanceItemMapper;
    private final EquipmentAlertMapper equipmentAlertMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;

    public List<EquipmentResponse> listEquipment() {
        var access = inspectionAccessScopeService.currentAccess();
        return equipmentMapper.selectList(Wrappers.lambdaQuery(Equipment.class)
                .eq(Equipment::getTenantCode, TenantContext.tenantCode()))
            .stream()
            .filter(equipment -> canAccessEquipment(equipment, access))
            .sorted((left, right) -> compareAssetNo(left.getAssetNo(), right.getAssetNo()))
            .map(EquipmentResponse::from)
            .toList();
    }

    public EquipmentPageResponse pageEquipment(EquipmentQueryRequest request) {
        var access = inspectionAccessScopeService.currentAccess();
        var query = Wrappers.lambdaQuery(Equipment.class)
            .eq(Equipment::getTenantCode, TenantContext.tenantCode());
        if (StringUtils.hasText(request.equipmentType())) {
            query.eq(Equipment::getEquipmentType, request.equipmentType().trim());
        }
        if (StringUtils.hasText(request.status())) {
            query.eq(Equipment::getStatus, request.status().trim().toUpperCase(Locale.ROOT));
        }
        String keyword = StringUtils.hasText(request.keyword()) ? request.keyword().trim().toLowerCase(Locale.ROOT) : null;
        List<EquipmentResponse> matches = equipmentMapper.selectList(query).stream()
            .filter(equipment -> canAccessEquipment(equipment, access))
            .filter(equipment -> matchesKeyword(equipment, keyword))
            .sorted((left, right) -> compareAssetNo(left.getAssetNo(), right.getAssetNo()))
            .map(EquipmentResponse::from)
            .toList();
        long total = matches.size();
        long page = request.resolvedPage();
        long pageSize = request.resolvedPageSize();
        long offset = (page - 1) * pageSize;
        List<EquipmentResponse> records = offset >= total ? List.of()
            : matches.subList((int) offset, (int) Math.min(offset + pageSize, total));
        return new EquipmentPageResponse(records, total, page, pageSize);
    }

    public List<EquipmentTypeResponse> listEquipmentTypes() {
        return java.util.Arrays.stream(EquipmentType.values())
            .map(EquipmentTypeResponse::from)
            .toList();
    }

    @Transactional
    public void deleteEquipment(String equipmentId) {
        Equipment equipment = requireEquipment(equipmentId);
        requireEquipmentAccess(equipment);
        long maintenanceCount = maintenanceMapper.selectCount(Wrappers.lambdaQuery(EquipmentMaintenance.class)
            .eq(EquipmentMaintenance::getTenantCode, TenantContext.tenantCode())
            .eq(EquipmentMaintenance::getEquipmentId, equipmentId));
        if (maintenanceCount > 0) {
            throw new IllegalArgumentException("设备已关联 " + maintenanceCount + " 条维护记录，不能删除");
        }
        equipmentMapper.deleteById(equipmentId);
    }

    public List<MaintenanceTemplateItemResponse> listMaintenanceTemplateItems(String equipmentType, String maintenanceType) {
        var query = Wrappers.lambdaQuery(EquipmentMaintenanceTemplateItem.class)
            .eq(EquipmentMaintenanceTemplateItem::getTenantCode, TenantContext.tenantCode())
            .eq(EquipmentMaintenanceTemplateItem::getStatus, "ACTIVE");
        if (StringUtils.hasText(equipmentType)) {
            query.eq(EquipmentMaintenanceTemplateItem::getEquipmentType, equipmentType.trim());
        }
        if (StringUtils.hasText(maintenanceType)) {
            query.eq(EquipmentMaintenanceTemplateItem::getMaintenanceType, normalizeMaintenanceType(maintenanceType));
        }
        return maintenanceTemplateItemMapper.selectList(query.orderByAsc(EquipmentMaintenanceTemplateItem::getItemCode)).stream()
            .map(MaintenanceTemplateItemResponse::from)
            .toList();
    }

    @Transactional
    public MaintenanceTemplateItemResponse saveMaintenanceTemplateItem(SaveMaintenanceTemplateItemRequest request) {
        requireFullEquipmentAccess();
        EquipmentType equipmentType = EquipmentType.requireSupported(request.equipmentType().trim());
        String maintenanceType = normalizeMaintenanceType(request.maintenanceType());
        if (request.lowerLimit() != null && request.upperLimit() != null && request.lowerLimit().compareTo(request.upperLimit()) > 0) {
            throw new IllegalArgumentException("标准下限不能大于上限");
        }
        String tenantCode = TenantContext.tenantCode();
        String itemCode = request.itemCode().trim().toUpperCase(Locale.ROOT);
        EquipmentMaintenanceTemplateItem item = maintenanceTemplateItemMapper.selectOne(Wrappers.lambdaQuery(EquipmentMaintenanceTemplateItem.class)
            .eq(EquipmentMaintenanceTemplateItem::getTenantCode, tenantCode)
            .eq(EquipmentMaintenanceTemplateItem::getEquipmentType, equipmentType.displayName())
            .eq(EquipmentMaintenanceTemplateItem::getMaintenanceType, maintenanceType)
            .eq(EquipmentMaintenanceTemplateItem::getItemCode, itemCode));
        LocalDateTime now = LocalDateTime.now();
        boolean isNew = item == null;
        if (isNew) {
            item = new EquipmentMaintenanceTemplateItem();
            item.setId(UUID.randomUUID().toString());
            item.setTenantCode(tenantCode);
            item.setEquipmentType(equipmentType.displayName());
            item.setMaintenanceType(maintenanceType);
            item.setItemCode(itemCode);
            item.setCreatedBy(TenantContext.actor());
            item.setCreatedAt(now);
        }
        item.setItemName(request.itemName().trim());
        item.setLowerLimit(request.lowerLimit());
        item.setUpperLimit(request.upperLimit());
        item.setUnit(StringUtils.hasText(request.unit()) ? request.unit().trim() : null);
        item.setStatus("ACTIVE");
        item.setUpdatedAt(now);
        if (isNew) {
            maintenanceTemplateItemMapper.insert(item);
        } else {
            maintenanceTemplateItemMapper.updateById(item);
        }
        return MaintenanceTemplateItemResponse.from(item);
    }

    @Transactional
    public void deleteMaintenanceTemplateItem(String id) {
        requireFullEquipmentAccess();
        EquipmentMaintenanceTemplateItem item = maintenanceTemplateItemMapper.selectById(id);
        if (item == null || !TenantContext.tenantCode().equals(item.getTenantCode())) {
            throw new IllegalArgumentException("维护项目不存在或无权操作");
        }
        maintenanceTemplateItemMapper.deleteById(id);
    }

    public List<MaintenanceItemResponse> listMaintenanceItems(String maintenanceId) {
        EquipmentMaintenance maintenance = requireMaintenance(maintenanceId);
        requireEquipmentAccess(requireEquipment(maintenance.getEquipmentId()));
        return maintenanceItemMapper.selectList(Wrappers.lambdaQuery(EquipmentMaintenanceItem.class)
                .eq(EquipmentMaintenanceItem::getTenantCode, TenantContext.tenantCode())
                .eq(EquipmentMaintenanceItem::getMaintenanceId, maintenanceId)
                .orderByAsc(EquipmentMaintenanceItem::getItemCode))
            .stream().map(MaintenanceItemResponse::from).toList();
    }

    public List<EquipmentAlertResponse> listEquipmentAlerts(String status) {
        var access = inspectionAccessScopeService.currentAccess();
        var query = Wrappers.lambdaQuery(EquipmentAlert.class).eq(EquipmentAlert::getTenantCode, TenantContext.tenantCode());
        if (StringUtils.hasText(status)) {
            query.eq(EquipmentAlert::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        return equipmentAlertMapper.selectList(query.orderByDesc(EquipmentAlert::getCreatedAt)).stream()
            .map(alert -> {
                Equipment equipment = equipmentMapper.selectById(alert.getEquipmentId());
                return equipment == null || !canAccessEquipment(equipment, access) ? null
                    : EquipmentAlertResponse.from(alert, equipment.getAssetNo(), equipment.getName());
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    @Transactional
    public EquipmentResponse createOrUpdateEquipment(CreateEquipmentRequest request) {
        String tenantCode = TenantContext.tenantCode();
        requireProcessOrFullAccess(request.processCode());
        EquipmentType equipmentType = EquipmentType.requireSupported(request.equipmentType().trim());
        Equipment equipment = equipmentMapper.selectOne(Wrappers.lambdaQuery(Equipment.class)
            .eq(Equipment::getTenantCode, tenantCode)
            .eq(Equipment::getAssetNo, request.assetNo().trim()));
        LocalDateTime now = LocalDateTime.now();
        boolean isNew = equipment == null;
        if (isNew) {
            equipment = new Equipment();
            equipment.setId(UUID.randomUUID().toString());
            equipment.setTenantCode(tenantCode);
            equipment.setAssetNo(request.assetNo().trim());
            equipment.setStatus("IN_USE");
            equipment.setCreatedAt(now);
        }
        equipment.setName(request.name().trim());
        equipment.setEquipmentType(equipmentType.displayName());
        equipment.setManufacturer(normalizeOptional(request.manufacturer()));
        equipment.setModelName(normalizeOptional(request.model()));
        equipment.setProcessCode(request.processCode());
        equipment.setProcessName(request.processName());
        equipment.setResponsibleName(request.responsibleName());
        equipment.setCalibrationDueAt(request.calibrationDueAt());
        equipment.setUpdatedAt(now);
        if (isNew) {
            equipmentMapper.insert(equipment);
        } else {
            equipmentMapper.updateById(equipment);
        }
        return EquipmentResponse.from(equipment);
    }

    @Transactional
    public EquipmentResponse updateEquipment(String equipmentId, UpdateEquipmentRequest request) {
        Equipment equipment = requireEquipment(equipmentId);
        requireEquipmentAccess(equipment);
        requireProcessOrFullAccess(request.processCode());
        EquipmentType equipmentType = EquipmentType.requireSupported(request.equipmentType().trim());
        equipment.setName(request.name().trim());
        equipment.setEquipmentType(equipmentType.displayName());
        equipment.setManufacturer(normalizeOptional(request.manufacturer()));
        equipment.setModelName(normalizeOptional(request.model()));
        equipment.setProcessCode(request.processCode());
        equipment.setProcessName(request.processName());
        equipment.setResponsibleName(request.responsibleName());
        equipment.setCalibrationDueAt(request.calibrationDueAt());
        equipment.setUpdatedAt(LocalDateTime.now());
        equipmentMapper.updateById(equipment);
        return EquipmentResponse.from(equipment);
    }

    public List<EquipmentMaintenanceResponse> listMaintenance(String status) {
        String tenantCode = TenantContext.tenantCode();
        var access = inspectionAccessScopeService.currentAccess();
        var query = Wrappers.lambdaQuery(EquipmentMaintenance.class)
            .eq(EquipmentMaintenance::getTenantCode, tenantCode);
        if (StringUtils.hasText(status)) {
            query.eq(EquipmentMaintenance::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        return maintenanceMapper.selectList(query.orderByDesc(EquipmentMaintenance::getScheduledAt)).stream()
            .filter(maintenance -> canAccessMaintenance(maintenance, access))
            .map(this::toMaintenanceResponse)
            .toList();
    }

    @Transactional
    public EquipmentMaintenanceResponse createMaintenance(CreateMaintenanceRequest request) {
        Equipment equipment = requireEquipment(request.equipmentId());
        requireEquipmentAccess(equipment);
        String maintenanceType = normalizeMaintenanceType(request.maintenanceType());
        List<EquipmentMaintenanceTemplateItem> templateItems = maintenanceTemplateItemMapper.selectList(Wrappers.lambdaQuery(EquipmentMaintenanceTemplateItem.class)
            .eq(EquipmentMaintenanceTemplateItem::getTenantCode, TenantContext.tenantCode())
            .eq(EquipmentMaintenanceTemplateItem::getEquipmentType, equipment.getEquipmentType())
            .eq(EquipmentMaintenanceTemplateItem::getMaintenanceType, maintenanceType)
            .eq(EquipmentMaintenanceTemplateItem::getStatus, "ACTIVE")
            .orderByAsc(EquipmentMaintenanceTemplateItem::getItemCode));
        if (templateItems.isEmpty() && !"REPAIR".equals(maintenanceType)) {
            throw new IllegalArgumentException("设备类型“" + equipment.getEquipmentType() + "”尚未配置" + maintenanceType + "维护项目，请先在项目模板中配置");
        }
        LocalDateTime now = LocalDateTime.now();
        EquipmentMaintenance maintenance = new EquipmentMaintenance();
        maintenance.setId(UUID.randomUUID().toString());
        maintenance.setTenantCode(TenantContext.tenantCode());
        maintenance.setRecordNo(nextMaintenanceNo());
        maintenance.setEquipmentId(equipment.getId());
        maintenance.setMaintenanceType(maintenanceType);
        maintenance.setScheduledAt(request.scheduledAt());
        maintenance.setStatus("PENDING");
        maintenance.setDescription(request.description().trim());
        maintenance.setExecutorName(request.executorName());
        maintenance.setCreatedBy(TenantContext.actor());
        maintenance.setCreatedAt(now);
        maintenance.setUpdatedAt(now);
        maintenanceMapper.insert(maintenance);
        for (EquipmentMaintenanceTemplateItem templateItem : templateItems) {
            EquipmentMaintenanceItem item = new EquipmentMaintenanceItem();
            item.setId(UUID.randomUUID().toString());
            item.setTenantCode(TenantContext.tenantCode());
            item.setMaintenanceId(maintenance.getId());
            item.setTemplateItemId(templateItem.getId());
            item.setItemCode(templateItem.getItemCode());
            item.setItemName(templateItem.getItemName());
            item.setLowerLimit(templateItem.getLowerLimit());
            item.setUpperLimit(templateItem.getUpperLimit());
            item.setUnit(templateItem.getUnit());
            item.setJudgement("PENDING");
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            maintenanceItemMapper.insert(item);
        }
        return EquipmentMaintenanceResponse.from(maintenance, equipment.getAssetNo(), equipment.getName());
    }

    @Transactional
    public EquipmentMaintenanceResponse completeMaintenance(String maintenanceId, CompleteMaintenanceRequest request) {
        EquipmentMaintenance maintenance = maintenanceMapper.selectById(maintenanceId);
        if (maintenance == null || !TenantContext.tenantCode().equals(maintenance.getTenantCode())) {
            throw new IllegalArgumentException("维护记录不存在或无权操作");
        }
        if ("COMPLETED".equals(maintenance.getStatus()) || "ALERT".equals(maintenance.getStatus())) {
            throw new IllegalArgumentException("该维护记录已完成");
        }
        Equipment equipment = requireEquipment(maintenance.getEquipmentId());
        requireEquipmentAccess(equipment);
        List<EquipmentMaintenanceItem> items = maintenanceItemMapper.selectList(Wrappers.lambdaQuery(EquipmentMaintenanceItem.class)
            .eq(EquipmentMaintenanceItem::getTenantCode, TenantContext.tenantCode())
            .eq(EquipmentMaintenanceItem::getMaintenanceId, maintenanceId));
        Map<String, BigDecimal> measuredValues = new HashMap<>();
        if (request.items() != null) {
            request.items().forEach(item -> measuredValues.put(item.maintenanceItemId(), item.measuredValue()));
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> abnormalItems = new java.util.ArrayList<>();
        for (EquipmentMaintenanceItem item : items) {
            BigDecimal value = measuredValues.get(item.getId());
            if (value == null) {
                throw new IllegalArgumentException("请填写维护项目“" + item.getItemName() + "”的实测值");
            }
            boolean qualified = isWithinStandard(value, item.getLowerLimit(), item.getUpperLimit());
            item.setMeasuredValue(value);
            item.setJudgement(qualified ? "PASS" : "FAIL");
            item.setUpdatedAt(now);
            maintenanceItemMapper.updateById(item);
            if (!qualified) {
                abnormalItems.add(item.getItemName() + "=" + value.toPlainString() + standardRange(item));
            }
        }
        boolean hasAbnormal = !abnormalItems.isEmpty();
        maintenance.setResult(request.result().trim() + (hasAbnormal ? "；项目异常：" + String.join("、", abnormalItems) : "；项目均符合标准"));
        maintenance.setStatus(hasAbnormal ? "ALERT" : "COMPLETED");
        maintenance.setCompletedAt(now);
        maintenance.setVerifierName(TenantContext.actor());
        maintenance.setUpdatedAt(now);
        maintenanceMapper.updateById(maintenance);
        if (hasAbnormal) {
            createEquipmentAlert(equipment, maintenance, abnormalItems, now);
        } else if ("REPAIR".equals(maintenance.getMaintenanceType()) || "CALIBRATION".equals(maintenance.getMaintenanceType())) {
            resolveOpenAlerts(equipment.getId(), maintenance.getId(), now);
        }
        return EquipmentMaintenanceResponse.from(maintenance, equipment.getAssetNo(), equipment.getName());
    }

    private Equipment requireEquipment(String id) {
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null || !TenantContext.tenantCode().equals(equipment.getTenantCode())) {
            throw new IllegalArgumentException("设备不存在或无权操作");
        }
        return equipment;
    }

    private EquipmentMaintenance requireMaintenance(String id) {
        EquipmentMaintenance maintenance = maintenanceMapper.selectById(id);
        if (maintenance == null || !TenantContext.tenantCode().equals(maintenance.getTenantCode())) {
            throw new IllegalArgumentException("维护记录不存在或无权操作");
        }
        return maintenance;
    }

    private String normalizeMaintenanceType(String value) {
        String maintenanceType = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!MAINTENANCE_TYPES.contains(maintenanceType)) {
            throw new IllegalArgumentException("维护类型仅支持 INSPECTION、MAINTENANCE、REPAIR 或 CALIBRATION");
        }
        return maintenanceType;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static int compareAssetNo(String left, String right) {
        Matcher leftSuffix = ASSET_NUMBER_SUFFIX.matcher(left);
        Matcher rightSuffix = ASSET_NUMBER_SUFFIX.matcher(right);
        if (leftSuffix.matches() && rightSuffix.matches()
            && leftSuffix.group(1).equalsIgnoreCase(rightSuffix.group(1))) {
            int numberOrder = new BigInteger(leftSuffix.group(2)).compareTo(new BigInteger(rightSuffix.group(2)));
            if (numberOrder != 0) {
                return numberOrder;
            }
        }
        int textOrder = left.compareToIgnoreCase(right);
        return textOrder != 0 ? textOrder : left.compareTo(right);
    }

    private void requireFullEquipmentAccess() {
        if (!inspectionAccessScopeService.currentAccess().fullDataAccess()) {
            throw new org.springframework.security.access.AccessDeniedException("仅租户管理员可维护设备项目模板");
        }
    }

    private boolean isWithinStandard(BigDecimal value, BigDecimal lowerLimit, BigDecimal upperLimit) {
        return (lowerLimit == null || value.compareTo(lowerLimit) >= 0)
            && (upperLimit == null || value.compareTo(upperLimit) <= 0);
    }

    private String standardRange(EquipmentMaintenanceItem item) {
        String lower = item.getLowerLimit() == null ? "-∞" : item.getLowerLimit().toPlainString();
        String upper = item.getUpperLimit() == null ? "+∞" : item.getUpperLimit().toPlainString();
        return "（标准 " + lower + "～" + upper + (StringUtils.hasText(item.getUnit()) ? item.getUnit() : "") + "）";
    }

    private void createEquipmentAlert(Equipment equipment, EquipmentMaintenance maintenance, List<String> abnormalItems, LocalDateTime now) {
        EquipmentAlert alert = new EquipmentAlert();
        alert.setId(UUID.randomUUID().toString());
        alert.setTenantCode(TenantContext.tenantCode());
        alert.setAlertNo(nextAlertNo());
        alert.setEquipmentId(equipment.getId());
        alert.setMaintenanceId(maintenance.getId());
        alert.setTitle(equipment.getName() + "维护项目超标");
        alert.setDescription(String.join("；", abnormalItems));
        alert.setStatus("OPEN");
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        equipmentAlertMapper.insert(alert);
        equipment.setStatus("ALARM");
        equipment.setUpdatedAt(now);
        equipmentMapper.updateById(equipment);
    }

    private void resolveOpenAlerts(String equipmentId, String repairMaintenanceId, LocalDateTime now) {
        equipmentAlertMapper.selectList(Wrappers.lambdaQuery(EquipmentAlert.class)
                .eq(EquipmentAlert::getTenantCode, TenantContext.tenantCode())
                .eq(EquipmentAlert::getEquipmentId, equipmentId)
                .eq(EquipmentAlert::getStatus, "OPEN"))
            .forEach(alert -> {
                alert.setStatus("RESOLVED");
                alert.setResolvedByMaintenanceId(repairMaintenanceId);
                alert.setResolvedAt(now);
                alert.setUpdatedAt(now);
                equipmentAlertMapper.updateById(alert);
            });
        Equipment equipment = requireEquipment(equipmentId);
        equipment.setStatus("IN_USE");
        equipment.setUpdatedAt(now);
        equipmentMapper.updateById(equipment);
    }

    private EquipmentMaintenanceResponse toMaintenanceResponse(EquipmentMaintenance maintenance) {
        Equipment equipment = equipmentMapper.selectById(maintenance.getEquipmentId());
        return EquipmentMaintenanceResponse.from(maintenance,
            equipment == null ? "已删除设备" : equipment.getAssetNo(),
            equipment == null ? "已删除设备" : equipment.getName());
    }

    private boolean canAccessEquipment(Equipment equipment, InspectionAccessScopeService.InspectionAccess access) {
        return access.fullDataAccess() || (StringUtils.hasText(equipment.getProcessCode()) && access.canAccess(equipment.getProcessCode()));
    }

    private boolean matchesKeyword(Equipment equipment, String keyword) {
        if (keyword == null) {
            return true;
        }
        return java.util.stream.Stream.of(equipment.getAssetNo(), equipment.getName(), equipment.getManufacturer(), equipment.getModelName(),
                equipment.getResponsibleName(), equipment.getProcessCode(), equipment.getProcessName())
            .filter(java.util.Objects::nonNull)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(value -> value.contains(keyword));
    }

    private boolean canAccessMaintenance(EquipmentMaintenance maintenance, InspectionAccessScopeService.InspectionAccess access) {
        Equipment equipment = equipmentMapper.selectById(maintenance.getEquipmentId());
        return equipment != null && TenantContext.tenantCode().equals(equipment.getTenantCode()) && canAccessEquipment(equipment, access);
    }

    private void requireEquipmentAccess(Equipment equipment) {
        requireProcessOrFullAccess(equipment.getProcessCode());
    }

    private void requireProcessOrFullAccess(String processCode) {
        if (StringUtils.hasText(processCode)) {
            inspectionAccessScopeService.requireProcessAccess(processCode.trim());
            return;
        }
        if (!inspectionAccessScopeService.currentAccess().fullDataAccess()) {
            throw new org.springframework.security.access.AccessDeniedException("检验员仅可管理本工序关联的设备");
        }
    }

    private String nextMaintenanceNo() {
        long sequence = maintenanceMapper.selectCount(Wrappers.lambdaQuery(EquipmentMaintenance.class)
            .eq(EquipmentMaintenance::getTenantCode, TenantContext.tenantCode())) + 1;
        return "EM-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", sequence);
    }

    private String nextAlertNo() {
        long sequence = equipmentAlertMapper.selectCount(Wrappers.lambdaQuery(EquipmentAlert.class)
            .eq(EquipmentAlert::getTenantCode, TenantContext.tenantCode())) + 1;
        return "EA-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", sequence);
    }
}
