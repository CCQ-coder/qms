package com.pcbqms.administration.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.access.application.InspectionAccessScopeService;
import com.pcbqms.administration.api.AdministrationAuditLogResponse;
import com.pcbqms.administration.api.AdministrationItemResponse;
import com.pcbqms.administration.api.MenuAccessResponse;
import com.pcbqms.administration.api.SaveAdministrationItemRequest;
import com.pcbqms.administration.domain.AdministrationAuditLog;
import com.pcbqms.administration.domain.AdministrationItem;
import com.pcbqms.administration.infrastructure.persistence.AdministrationAuditLogMapper;
import com.pcbqms.administration.infrastructure.persistence.AdministrationItemMapper;
import com.pcbqms.common.tenancy.TenantContext;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdministrationApplicationService {
    private static final Set<String> CATALOG_TYPES = Set.of(
        "ROLE", "MENU", "ORGANIZATION", "DEPARTMENT", "SCRAP_REASON", "DICTIONARY", "INTERFACE", "APPROVAL_FLOW", "EQUIPMENT_TEMPLATE"
    );
    private static final Set<String> STATUS_CODES = Set.of("ACTIVE", "INACTIVE");
    private static final List<MenuDefinition> DEFAULT_MENU_DEFINITIONS = List.of(
        new MenuDefinition("OVERVIEW", "总览", null, 10),
        new MenuDefinition("DASHBOARD", "质量总览", "OVERVIEW", 11),
        new MenuDefinition("QUALITY", "质量检测", null, 20),
        new MenuDefinition("WORK_ORDERS", "生产指令查询", "QUALITY", 21),
        new MenuDefinition("PROCESS_INSPECTION", "工序检验", "QUALITY", 22),
        new MenuDefinition("INSPECTION_WORKBENCH", "成品测试", "QUALITY", 23),
        new MenuDefinition("INSPECTION_TEMPLATES", "检验标准配置", "QUALITY", 24),
        new MenuDefinition("INSPECTION_RULES", "自动派单规则", "QUALITY", 25),
        new MenuDefinition("TRACE", "报表", null, 30),
        new MenuDefinition("NONCONFORMITIES", "不良品处理", "QUALITY", 26),
        new MenuDefinition("TRACEABILITY", "质量追溯", "TRACE", 32),
        new MenuDefinition("ANALYTICS", "报表分析", "TRACE", 33),
        new MenuDefinition("EQUIPMENT_FINISHED_TEST_SUMMARY", "成品测试汇总表", "TRACE", 34),
        new MenuDefinition("EQUIPMENT", "设备管理", null, 40),
        new MenuDefinition("EQUIPMENT_LEDGER", "设备台账", "EQUIPMENT", 41),
        new MenuDefinition("EQUIPMENT_INSPECTION", "点检管理", "EQUIPMENT", 42),
        new MenuDefinition("EQUIPMENT_MAINTENANCE", "保养管理", "EQUIPMENT", 43),
        new MenuDefinition("EQUIPMENT_REPAIR", "维修管理", "EQUIPMENT", 44),
        new MenuDefinition("EQUIPMENT_ALERTS", "设备告警", "EQUIPMENT", 45),
        new MenuDefinition("EQUIPMENT_TEMPLATES", "项目模板", "EQUIPMENT", 46),
        new MenuDefinition("EQUIPMENT_GATEWAY_MAPPINGS", "设备网关映射", "EQUIPMENT", 47),
        new MenuDefinition("EQUIPMENT_RUNTIME_MONITOR", "设备数据接入", "EQUIPMENT", 47),
        new MenuDefinition("SYSTEM", "系统管理", null, 50),
        new MenuDefinition("SYSTEM_USERS", "用户管理", "SYSTEM", 51),
        new MenuDefinition("SYSTEM_ROLES", "角色权限", "SYSTEM", 52),
        new MenuDefinition("SYSTEM_MENU_MANAGEMENT", "菜单管理", "SYSTEM", 53),
        new MenuDefinition("SYSTEM_ORGANIZATIONS", "组织机构", "SYSTEM", 54),
        new MenuDefinition("SYSTEM_DEPARTMENTS", "部门设置", "SYSTEM", 55),
        new MenuDefinition("SYSTEM_PROCESSES", "工序设置", "SYSTEM", 56),
        new MenuDefinition("SYSTEM_SCRAP_REASONS", "报废原因设置", "SYSTEM", 57),
        new MenuDefinition("SYSTEM_DICTIONARIES", "字典管理", "SYSTEM", 58),
        new MenuDefinition("SYSTEM_PARAMETERS", "系统参数", "SYSTEM", 59),
        new MenuDefinition("SYSTEM_INTERFACES", "接口配置", "SYSTEM", 60),
        new MenuDefinition("SYSTEM_APPROVAL_FLOW", "审批流", "SYSTEM", 61),
        new MenuDefinition("SYSTEM_OPERATION_LOG", "操作日志", "SYSTEM", 62),
        new MenuDefinition("SYSTEM_TENANTS", "租户管理", "SYSTEM", 63)
    );

    public static List<MenuDefinition> defaultMenuDefinitions() {
        return DEFAULT_MENU_DEFINITIONS;
    }

    private final AdministrationItemMapper administrationItemMapper;
    private final AdministrationAuditLogMapper administrationAuditLogMapper;
    private final InspectionAccessScopeService inspectionAccessScopeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<AdministrationItemResponse> listItems(String requestedCatalogType) {
        inspectionAccessScopeService.requireTenantAdministrator();
        String catalogType = normalizeCatalogType(requestedCatalogType);
        if ("MENU".equals(catalogType)) {
            ensureDefaultMenuItems();
        }
        return listItemsForTenant(catalogType)
            .stream().map(this::toResponse).toList();
    }

    /**
     * Returns the menu configuration that applies to the signed-in user.  This endpoint deliberately
     * has no administrator check: every authenticated user needs it to build the navigation safely.
     */
    @Transactional
    public MenuAccessResponse menuAccess() {
        var access = inspectionAccessScopeService.currentAccess();
        ensureDefaultMenuItems();
        List<AdministrationItem> menus = listItemsForTenant("MENU");
        Set<String> knownMenuCodes = menus.stream().map(AdministrationItem::getItemCode).collect(java.util.stream.Collectors.toSet());
        List<String> activeMenuCodes = menus.stream()
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .map(AdministrationItem::getItemCode)
            .toList();

        AdministrationItem role = administrationItemMapper.selectOne(Wrappers.lambdaQuery(AdministrationItem.class)
            .eq(AdministrationItem::getTenantCode, TenantContext.tenantCode())
            .eq(AdministrationItem::getCatalogType, "ROLE")
            .eq(AdministrationItem::getItemCode, access.roleCode()));
        Map<String, String> roleAttributes = role == null ? Map.of() : readAttributes(role.getAttributesJson());
        boolean menuSelectionConfigured = "true".equalsIgnoreCase(roleAttributes.get("menuCodesConfigured"));
        Set<String> permittedMenuCodes = menuSelectionConfigured
            ? parseMenuCodes(roleAttributes.get("menuCodes"), knownMenuCodes)
            : knownMenuCodes;

        return new MenuAccessResponse(access.roleCode(), activeMenuCodes, new ArrayList<>(permittedMenuCodes));
    }

    @Transactional
    public AdministrationItemResponse createItem(String requestedCatalogType, SaveAdministrationItemRequest request) {
        inspectionAccessScopeService.requireTenantAdministrator();
        String catalogType = normalizeCatalogType(requestedCatalogType);
        String itemCode = normalizeCode(request.itemCode());
        String tenantCode = TenantContext.tenantCode();
        if (administrationItemMapper.selectCount(Wrappers.lambdaQuery(AdministrationItem.class)
            .eq(AdministrationItem::getTenantCode, tenantCode)
            .eq(AdministrationItem::getCatalogType, catalogType)
            .eq(AdministrationItem::getItemCode, itemCode)) > 0) {
            throw new IllegalArgumentException("该模块中已存在编码：" + itemCode);
        }

        LocalDateTime now = LocalDateTime.now();
        AdministrationItem item = new AdministrationItem();
        item.setId(UUID.randomUUID().toString());
        item.setTenantCode(tenantCode);
        item.setCatalogType(catalogType);
        item.setItemCode(itemCode);
        item.setCreatedBy(TenantContext.actor());
        item.setCreatedAt(now);
        apply(item, request, now);
        administrationItemMapper.insert(item);
        writeAudit(catalogType, "CREATE", itemCode, "新建" + catalogLabel(catalogType) + "：" + item.getItemName(), now);
        return toResponse(item);
    }

    @Transactional
    public AdministrationItemResponse updateItem(String requestedCatalogType, String id, SaveAdministrationItemRequest request) {
        inspectionAccessScopeService.requireTenantAdministrator();
        String catalogType = normalizeCatalogType(requestedCatalogType);
        AdministrationItem item = requireItem(catalogType, id);
        String itemCode = normalizeCode(request.itemCode());
        if (!item.getItemCode().equals(itemCode)) {
            throw new IllegalArgumentException("配置编码创建后不可修改");
        }
        LocalDateTime now = LocalDateTime.now();
        apply(item, request, now);
        administrationItemMapper.updateById(item);
        writeAudit(catalogType, "UPDATE", item.getItemCode(), "更新" + catalogLabel(catalogType) + "：" + item.getItemName(), now);
        return toResponse(item);
    }

    public List<AdministrationAuditLogResponse> listAuditLogs(String module, Integer limit) {
        inspectionAccessScopeService.requireTenantAdministrator();
        int safeLimit = limit == null ? 100 : Math.min(Math.max(limit, 1), 300);
        var query = Wrappers.lambdaQuery(AdministrationAuditLog.class)
            .eq(AdministrationAuditLog::getTenantCode, TenantContext.tenantCode());
        if (StringUtils.hasText(module)) {
            query.eq(AdministrationAuditLog::getModuleCode, module.trim().toUpperCase(Locale.ROOT));
        }
        return administrationAuditLogMapper.selectList(query.orderByDesc(AdministrationAuditLog::getCreatedAt)
                .last("LIMIT " + safeLimit))
            .stream().map(AdministrationAuditLogResponse::from).toList();
    }

    private void apply(AdministrationItem item, SaveAdministrationItemRequest request, LocalDateTime now) {
        String status = StringUtils.hasText(request.status()) ? request.status().trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        if (!STATUS_CODES.contains(status)) {
            throw new IllegalArgumentException("状态仅支持 ACTIVE 或 INACTIVE");
        }
        item.setItemName(request.itemName().trim());
        item.setParentCode(StringUtils.hasText(request.parentCode()) ? normalizeCode(request.parentCode()) : null);
        item.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        item.setStatus(status);
        item.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        item.setAttributesJson(writeAttributes(normalizedAttributes(request.attributes())));
        item.setUpdatedBy(TenantContext.actor());
        item.setUpdatedAt(now);
    }

    private AdministrationItem requireItem(String catalogType, String id) {
        AdministrationItem item = administrationItemMapper.selectOne(Wrappers.lambdaQuery(AdministrationItem.class)
            .eq(AdministrationItem::getId, id)
            .eq(AdministrationItem::getTenantCode, TenantContext.tenantCode())
            .eq(AdministrationItem::getCatalogType, catalogType));
        if (item == null) {
            throw new IllegalArgumentException("配置不存在或无权访问");
        }
        return item;
    }

    private List<AdministrationItem> listItemsForTenant(String catalogType) {
        return administrationItemMapper.selectList(Wrappers.lambdaQuery(AdministrationItem.class)
            .eq(AdministrationItem::getTenantCode, TenantContext.tenantCode())
            .eq(AdministrationItem::getCatalogType, catalogType)
            .orderByAsc(AdministrationItem::getSortOrder)
            .orderByAsc(AdministrationItem::getItemCode));
    }

    private void ensureDefaultMenuItems() {
        String tenantCode = TenantContext.tenantCode();
        Set<String> existingCodes = new HashSet<>(administrationItemMapper.selectList(Wrappers.lambdaQuery(AdministrationItem.class)
                .eq(AdministrationItem::getTenantCode, tenantCode)
                .eq(AdministrationItem::getCatalogType, "MENU"))
            .stream().map(AdministrationItem::getItemCode).toList());
        LocalDateTime now = LocalDateTime.now();
        for (MenuDefinition definition : DEFAULT_MENU_DEFINITIONS) {
            if (existingCodes.contains(definition.code())) {
                continue;
            }
            AdministrationItem item = new AdministrationItem();
            item.setId(UUID.randomUUID().toString());
            item.setTenantCode(tenantCode);
            item.setCatalogType("MENU");
            item.setItemCode(definition.code());
            item.setItemName(definition.name());
            item.setParentCode(definition.parentCode());
            item.setDescription(definition.parentCode() == null ? "系统导航分组" : "系统导航页面");
            item.setStatus("ACTIVE");
            item.setSortOrder(definition.sortOrder());
            item.setAttributesJson(writeAttributes(Map.of("menuType", definition.parentCode() == null ? "GROUP" : "PAGE")));
            item.setCreatedBy("system");
            item.setUpdatedBy("system");
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            administrationItemMapper.insert(item);
        }
    }

    private Set<String> parseMenuCodes(String rawCodes, Set<String> knownMenuCodes) {
        if (!StringUtils.hasText(rawCodes)) {
            return Set.of();
        }
        Set<String> menuCodes = new java.util.LinkedHashSet<>();
        Arrays.stream(rawCodes.split(","))
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .filter(knownMenuCodes::contains)
            .forEach(menuCodes::add);
        return menuCodes;
    }

    private AdministrationItemResponse toResponse(AdministrationItem item) {
        return AdministrationItemResponse.from(item, readAttributes(item.getAttributesJson()));
    }

    private void writeAudit(String moduleCode, String operation, String targetCode, String summary, LocalDateTime now) {
        AdministrationAuditLog auditLog = new AdministrationAuditLog();
        auditLog.setId(UUID.randomUUID().toString());
        auditLog.setTenantCode(TenantContext.tenantCode());
        auditLog.setModuleCode(moduleCode);
        auditLog.setOperation(operation);
        auditLog.setTargetCode(targetCode);
        auditLog.setSummary(summary);
        auditLog.setActor(TenantContext.actor());
        auditLog.setCreatedAt(now);
        administrationAuditLogMapper.insert(auditLog);
    }

    private String normalizeCatalogType(String requestedCatalogType) {
        String catalogType = requestedCatalogType == null ? "" : requestedCatalogType.trim().toUpperCase(Locale.ROOT);
        if (!CATALOG_TYPES.contains(catalogType)) {
            throw new IllegalArgumentException("不支持的管理配置类型");
        }
        return catalogType;
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("编码不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, String> normalizedAttributes(Map<String, String> requestedAttributes) {
        if (requestedAttributes == null || requestedAttributes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        requestedAttributes.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null && StringUtils.hasText(value)) {
                attributes.put(key.trim(), value.trim());
            }
        });
        return attributes;
    }

    private String writeAttributes(Map<String, String> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("扩展配置格式不正确");
        }
    }

    private Map<String, String> readAttributes(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("管理配置数据格式错误", exception);
        }
    }

    private String catalogLabel(String catalogType) {
        return Map.of(
            "ROLE", "角色",
            "MENU", "菜单",
            "ORGANIZATION", "组织机构",
            "DEPARTMENT", "部门",
            "SCRAP_REASON", "报废原因",
            "DICTIONARY", "数据字典",
            "INTERFACE", "接口配置",
            "APPROVAL_FLOW", "审批流",
            "EQUIPMENT_TEMPLATE", "设备项目模板"
        ).get(catalogType);
    }

    public record MenuDefinition(String code, String name, String parentCode, int sortOrder) { }
}
