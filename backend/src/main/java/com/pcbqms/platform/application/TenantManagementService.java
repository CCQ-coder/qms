package com.pcbqms.platform.application;

import com.pcbqms.administration.application.AdministrationApplicationService;
import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.config.LocalPasswordAuthenticationProperties;
import com.pcbqms.platform.api.SaveTenantRequest;
import com.pcbqms.platform.api.TenantManagementResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Platform-only tenant provisioning. Queries use JdbcTemplate so the tenant guard cannot scope them. */
@Service
@RequiredArgsConstructor
public class TenantManagementService {
    private static final Set<String> STATUS_CODES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> PROVISIONING_TABLES = Set.of(
        "qms_administration_item", "qms_system_parameter", "qms_user_access_profile", "qms_user_process_scope"
    );
    private static final Map<String, String> TENANT_DATA_LABELS = Map.ofEntries(
        Map.entry("qms_equipment", "设备"),
        Map.entry("qms_equipment_telemetry", "设备采集记录"),
        Map.entry("qms_equipment_gateway_mapping", "设备网关映射"),
        Map.entry("qms_work_order", "工单"),
        Map.entry("qms_production_batch", "生产批次"),
        Map.entry("qms_inspection_task", "检验任务"),
        Map.entry("qms_inspection_record", "检验记录"),
        Map.entry("qms_customer_order", "客户订单"),
        Map.entry("qms_customer_part", "客户料号"),
        Map.entry("qms_material", "物料"),
        Map.entry("qms_material_issue", "领料记录"),
        Map.entry("qms_finished_goods_receipt", "成品入库记录"),
        Map.entry("qms_delivery_note", "送货单")
    );

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final LocalPasswordAuthenticationProperties localPasswordAuthenticationProperties;

    public List<TenantManagementResponse> list() {
        return jdbcTemplate.query("""
            SELECT id, code, name, status, login_pinned, created_at
            FROM qms_tenant
            ORDER BY login_pinned DESC, name, code
            """, tenantRowMapper()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TenantManagementResponse create(SaveTenantRequest request) {
        TenantDraft draft = validate(request, true);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM qms_tenant WHERE code = ?", Integer.class, draft.code());
        if (count != null && count > 0) {
            throw new IllegalArgumentException("租户编码已存在：" + draft.code());
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
            INSERT INTO qms_tenant (id, code, name, status, login_pinned, created_at)
            VALUES (?, ?, ?, ?, 0, ?)
            """, UUID.randomUUID().toString(), draft.code(), draft.name(), draft.status(), now);
        createAdministrator(draft, now);
        synchronizeMenus(draft.code(), draft.menuCodes(), now);
        return toResponse(requireTenant(draft.code()));
    }

    @Transactional
    public TenantManagementResponse update(String pathCode, SaveTenantRequest request) {
        String code = normalizeCode(pathCode, "租户编码");
        TenantRow existing = requireTenant(code);
        TenantDraft draft = validate(request, false);
        if (!code.equals(draft.code())) {
            throw new IllegalArgumentException("租户编码创建后不可修改");
        }

        if (existing.loginPinned() && !"ACTIVE".equals(draft.status())) {
            throw new IllegalArgumentException("登录页置顶租户不能停用，请先取消置顶");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("UPDATE qms_tenant SET name = ?, status = ? WHERE code = ?", draft.name(), draft.status(), code);
        updateAdministrator(code, draft, now);
        synchronizeMenus(code, draft.menuCodes(), now);
        return toResponse(requireTenant(code));
    }

    @Transactional
    public TenantManagementResponse updateLoginPinned(String pathCode, boolean loginPinned) {
        String code = normalizeCode(pathCode, "租户编码");
        TenantRow tenant = requireTenant(code);
        if (loginPinned) {
            if (!"ACTIVE".equals(tenant.status())) {
                throw new IllegalArgumentException("仅启用中的租户可以置顶到登录页");
            }
            Integer loginEnabledUserCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM qms_user_access_profile
                WHERE tenant_code = ? AND status = 'ACTIVE'
                  AND password_hash IS NOT NULL AND password_hash <> ''
                """, Integer.class, code);
            if (loginEnabledUserCount == null || loginEnabledUserCount == 0) {
                throw new IllegalArgumentException("置顶租户必须先配置可登录的管理员账号");
            }
            jdbcTemplate.update("UPDATE qms_tenant SET login_pinned = 0 WHERE login_pinned = 1");
        }
        jdbcTemplate.update("UPDATE qms_tenant SET login_pinned = ? WHERE code = ?", loginPinned, code);
        return toResponse(requireTenant(code));
    }

    /**
     * Removes an unused tenant together with the account and menu records created during provisioning.
     * Business and audit records deliberately block the operation so a tenant cannot be deleted by mistake.
     */
    @Transactional
    public void delete(String pathCode) {
        String code = normalizeCode(pathCode, "租户编码");
        TenantRow tenant = requireTenant(code);
        if (code.equals(TenantContext.tenantCode())) {
            throw new IllegalArgumentException("不能删除当前登录的平台注册租户");
        }
        if (tenant.loginPinned()) {
            throw new IllegalArgumentException("登录页置顶租户不能删除，请先取消置顶");
        }

        List<TenantDataCount> businessData = findBusinessData(code);
        if (!businessData.isEmpty()) {
            String summary = businessData.stream()
                .limit(5)
                .map(item -> tenantDataLabel(item.tableName()) + " " + item.count() + " 条")
                .collect(java.util.stream.Collectors.joining("、"));
            String suffix = businessData.size() > 5 ? "等" : "";
            throw new IllegalArgumentException("租户存在关联业务数据（" + summary + suffix
                + "），不能删除。请先迁移或清理业务数据");
        }

        jdbcTemplate.update("DELETE FROM qms_user_process_scope WHERE tenant_code = ?", code);
        jdbcTemplate.update("DELETE FROM qms_user_access_profile WHERE tenant_code = ?", code);
        jdbcTemplate.update("DELETE FROM qms_administration_item WHERE tenant_code = ?", code);
        jdbcTemplate.update("DELETE FROM qms_system_parameter WHERE tenant_code = ?", code);
        jdbcTemplate.update("DELETE FROM qms_tenant WHERE code = ?", code);
    }

    private TenantManagementResponse toResponse(TenantRow tenant) {
        AdministratorRow administrator = jdbcTemplate.query("""
            SELECT user_name, display_name
            FROM qms_user_access_profile
            WHERE tenant_code = ? AND role_code IN ('PLATFORM_ADMIN', 'TENANT_ADMIN')
            ORDER BY CASE role_code WHEN 'PLATFORM_ADMIN' THEN 0 ELSE 1 END, created_at, user_name
            LIMIT 1
            """, administratorRowMapper(), tenant.code()).stream().findFirst().orElse(null);
        return new TenantManagementResponse(
            tenant.id(), tenant.code(), tenant.name(), tenant.status(),
            administrator == null ? null : administrator.userName(),
            administrator == null ? null : administrator.displayName(),
            tenant.loginPinned(),
            activePageMenuCodes(tenant.code()), tenant.createdAt());
    }

    private void createAdministrator(TenantDraft draft, LocalDateTime now) {
        String passwordHash = passwordHash(draft.adminPassword(), true);
        jdbcTemplate.update("""
            INSERT INTO qms_user_access_profile
              (id, tenant_code, user_name, display_name, role_code, status, password_hash, password_changed_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'TENANT_ADMIN', 'ACTIVE', ?, ?, ?, ?)
            """, UUID.randomUUID().toString(), draft.code(), draft.adminUserName(), draft.adminDisplayName(), passwordHash,
            passwordHash == null ? null : now, now, now);
    }

    private void updateAdministrator(String tenantCode, TenantDraft draft, LocalDateTime now) {
        AdministratorRow existing = jdbcTemplate.query("""
            SELECT user_name, display_name
            FROM qms_user_access_profile
            WHERE tenant_code = ? AND role_code IN ('PLATFORM_ADMIN', 'TENANT_ADMIN')
            ORDER BY CASE role_code WHEN 'PLATFORM_ADMIN' THEN 0 ELSE 1 END, created_at, user_name
            LIMIT 1
            """, administratorRowMapper(), tenantCode).stream().findFirst().orElse(null);
        if (existing == null) {
            createAdministrator(draft, now);
            return;
        }
        if (!existing.userName().equals(draft.adminUserName())) {
            throw new IllegalArgumentException("租户初始管理员账号创建后不可修改");
        }
        String passwordHash = passwordHash(draft.adminPassword(), false);
        if (passwordHash == null) {
            jdbcTemplate.update("""
                UPDATE qms_user_access_profile
                SET display_name = ?, updated_at = ?
                WHERE tenant_code = ? AND user_name = ?
                """, draft.adminDisplayName(), now, tenantCode, existing.userName());
        } else {
            jdbcTemplate.update("""
                UPDATE qms_user_access_profile
                SET display_name = ?, password_hash = ?, password_changed_at = ?, updated_at = ?
                WHERE tenant_code = ? AND user_name = ?
                """, draft.adminDisplayName(), passwordHash, now, now, tenantCode, existing.userName());
        }
    }

    private void synchronizeMenus(String tenantCode, Set<String> requestedPageCodes, LocalDateTime now) {
        Set<String> activeCodes = new HashSet<>(requestedPageCodes);
        for (AdministrationApplicationService.MenuDefinition definition : AdministrationApplicationService.defaultMenuDefinitions()) {
            if (activeCodes.contains(definition.code()) && definition.parentCode() != null) {
                activeCodes.add(definition.parentCode());
            }
        }
        // The platform tenant must retain its own tenant-management entry, even if its form is saved.
        if (tenantCode.equals(TenantContext.tenantCode())) {
            activeCodes.add("SYSTEM_TENANTS");
            activeCodes.add("SYSTEM");
        }
        String actor = TenantContext.actor();
        for (AdministrationApplicationService.MenuDefinition definition : AdministrationApplicationService.defaultMenuDefinitions()) {
            jdbcTemplate.update("""
                INSERT INTO qms_administration_item
                  (id, tenant_code, catalog_type, item_code, item_name, parent_code, description, status, sort_order,
                   attributes_json, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'MENU', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  item_name = VALUES(item_name), parent_code = VALUES(parent_code), description = VALUES(description),
                  status = VALUES(status), sort_order = VALUES(sort_order), updated_by = VALUES(updated_by), updated_at = VALUES(updated_at)
                """, UUID.randomUUID().toString(), tenantCode, definition.code(), definition.name(), definition.parentCode(),
                definition.parentCode() == null ? "系统导航分组" : "系统导航页面",
                activeCodes.contains(definition.code()) ? "ACTIVE" : "INACTIVE", definition.sortOrder(),
                "{\"menuType\":\"" + (definition.parentCode() == null ? "GROUP" : "PAGE") + "\"}", actor, actor, now, now);
        }
    }

    private List<String> activePageMenuCodes(String tenantCode) {
        return jdbcTemplate.query("""
            SELECT item_code
            FROM qms_administration_item
            WHERE tenant_code = ? AND catalog_type = 'MENU' AND status = 'ACTIVE' AND parent_code IS NOT NULL
            ORDER BY sort_order, item_code
            """, (resultSet, rowNumber) -> resultSet.getString("item_code"), tenantCode);
    }

    private List<TenantDataCount> findBusinessData(String tenantCode) {
        List<String> tenantTables = jdbcTemplate.queryForList("""
            SELECT DISTINCT table_name
            FROM information_schema.columns
            WHERE table_schema = DATABASE() AND column_name = 'tenant_code'
            ORDER BY table_name
            """, String.class);
        return tenantTables.stream()
            .filter(tableName -> !PROVISIONING_TABLES.contains(tableName))
            .map(tableName -> new TenantDataCount(tableName, tenantRowCount(tableName, tenantCode)))
            .filter(item -> item.count() > 0)
            .toList();
    }

    private int tenantRowCount(String tableName, String tenantCode) {
        if (!tableName.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("检测到不安全的租户数据表名");
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `" + tableName + "` WHERE tenant_code = ?", Integer.class, tenantCode);
        return count == null ? 0 : count;
    }

    private String tenantDataLabel(String tableName) {
        return TENANT_DATA_LABELS.getOrDefault(tableName, tableName);
    }

    private TenantDraft validate(SaveTenantRequest request, boolean creating) {
        String code = normalizeCode(request.code(), "租户编码");
        String name = required(request.name(), "租户名称");
        String adminUserName = required(request.adminUserName(), "管理员账号");
        String adminDisplayName = StringUtils.hasText(request.adminDisplayName()) ? request.adminDisplayName().trim() : adminUserName;
        String status = StringUtils.hasText(request.status()) ? request.status().trim().toUpperCase(Locale.ROOT) : "ACTIVE";
        if (!STATUS_CODES.contains(status)) {
            throw new IllegalArgumentException("租户状态仅支持 ACTIVE 或 INACTIVE");
        }
        Set<String> menuCodes = normalizeMenuCodes(request.menuCodes());
        if (creating && localPasswordAuthenticationProperties.isEnabled() && !StringUtils.hasText(request.adminPassword())) {
            throw new IllegalArgumentException("创建租户时必须设置初始管理员密码");
        }
        return new TenantDraft(code, name, adminUserName, adminDisplayName, request.adminPassword(), status, menuCodes);
    }

    private Set<String> normalizeMenuCodes(List<String> requestedCodes) {
        Set<String> allowedPageCodes = AdministrationApplicationService.defaultMenuDefinitions().stream()
            .filter(definition -> definition.parentCode() != null)
            .map(AdministrationApplicationService.MenuDefinition::code)
            .filter(code -> !"SYSTEM_TENANTS".equals(code))
            .collect(java.util.stream.Collectors.toSet());
        if (requestedCodes == null) {
            return new LinkedHashSet<>(allowedPageCodes);
        }
        Set<String> normalized = requestedCodes.stream()
            .filter(StringUtils::hasText)
            .map(code -> code.trim().toUpperCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个可用菜单");
        }
        if (!allowedPageCodes.containsAll(normalized)) {
            throw new IllegalArgumentException("包含不支持的租户菜单");
        }
        return normalized;
    }

    private String passwordHash(String password, boolean required) {
        if (!StringUtils.hasText(password)) {
            if (required && localPasswordAuthenticationProperties.isEnabled()) {
                throw new IllegalArgumentException("初始管理员密码不能为空");
            }
            return null;
        }
        if (password.length() < 10) {
            throw new IllegalArgumentException("管理员密码至少需要 10 位");
        }
        return passwordEncoder.encode(password);
    }

    private TenantRow requireTenant(String code) {
        return jdbcTemplate.query("SELECT id, code, name, status, login_pinned, created_at FROM qms_tenant WHERE code = ?", tenantRowMapper(), code)
            .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("租户不存在：" + code));
    }

    private RowMapper<TenantRow> tenantRowMapper() {
        return (resultSet, rowNumber) -> new TenantRow(resultSet.getString("id"), resultSet.getString("code"),
            resultSet.getString("name"), resultSet.getString("status"), resultSet.getBoolean("login_pinned"),
            resultSet.getTimestamp("created_at").toLocalDateTime());
    }

    private RowMapper<AdministratorRow> administratorRowMapper() {
        return (resultSet, rowNumber) -> new AdministratorRow(resultSet.getString("user_name"), resultSet.getString("display_name"));
    }

    private String normalizeCode(String value, String label) {
        return required(value, label).toUpperCase(Locale.ROOT);
    }

    private String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    private record TenantRow(String id, String code, String name, String status, boolean loginPinned,
                             LocalDateTime createdAt) { }

    private record AdministratorRow(String userName, String displayName) { }

    private record TenantDataCount(String tableName, int count) { }

    private record TenantDraft(String code, String name, String adminUserName, String adminDisplayName,
                               String adminPassword, String status, Set<String> menuCodes) { }
}
