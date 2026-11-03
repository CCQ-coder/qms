package com.pcbqms.platform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.config.LocalPasswordAuthenticationProperties;
import com.pcbqms.platform.api.SaveTenantRequest;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

class TenantManagementServiceTest {
    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void newTenantGetsFlyingProbeTemplateWithAllThreeCounters() {
        TenantContext.set("PLATFORM", "admin");
        JdbcTemplate jdbcTemplate = tenantJdbc();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);

        service(jdbcTemplate)
            .create(new SaveTenantRequest("NEW-TENANT", "New Tenant", "admin", null, null, "ACTIVE", null));

        assertThat(writes(jdbcTemplate)).contains("INSERT INTO qms_inspection_template", "INSERT INTO qms_inspection_template_item",
            "FLYING_PROBE_ELECTRICAL_V1", "testedBoardCount", "goodBoardCount", "badBoardCount");
    }

    @Test
    void unusedTenantDeletesOnlyItsPristineSystemTemplate() {
        TenantContext.set("PLATFORM", "admin");
        JdbcTemplate jdbcTemplate = tenantJdbc();
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(
            List.of("qms_inspection_template", "qms_inspection_template_item"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("NEW-TENANT")))
            .thenReturn(List.of("template-id"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(0);

        service(jdbcTemplate).delete("NEW-TENANT");

        String writes = writes(jdbcTemplate);
        assertThat(writes).contains("DELETE FROM qms_inspection_template_item", "DELETE FROM qms_inspection_template");
        assertThat(writes).contains("DELETE FROM qms_tenant");
    }

    @Test
    void editedFlyingProbeTemplateBlocksTenantDeletion() {
        TenantContext.set("PLATFORM", "admin");
        JdbcTemplate jdbcTemplate = tenantJdbc();
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(
            List.of("qms_inspection_template", "qms_inspection_template_item"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("NEW-TENANT")))
            .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any())).thenAnswer(call ->
            ((String) call.getArgument(0)).contains("`qms_inspection_template`") ? 1 : 0);

        assertThatThrownBy(() -> service(jdbcTemplate).delete("NEW-TENANT"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("关联业务数据");
        assertThat(writes(jdbcTemplate)).doesNotContain("DELETE FROM qms_inspection_template");
    }

    private TenantManagementService service(JdbcTemplate jdbcTemplate) {
        return new TenantManagementService(jdbcTemplate, mock(PasswordEncoder.class),
            new LocalPasswordAuthenticationProperties());
    }

    private JdbcTemplate tenantJdbc() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (!sql.contains("FROM qms_tenant WHERE code = ?")) return List.of();
            ResultSet row = mock(ResultSet.class);
            when(row.getString("id")).thenReturn("tenant-id");
            when(row.getString("code")).thenReturn("NEW-TENANT");
            when(row.getString("name")).thenReturn("New Tenant");
            when(row.getString("status")).thenReturn("ACTIVE");
            when(row.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 2, 9, 0)));
            RowMapper<?> mapper = call.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        return jdbcTemplate;
    }

    private String writes(JdbcTemplate jdbcTemplate) {
        return mockingDetails(jdbcTemplate).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals("update"))
            .map(call -> (String) call.getArgument(0))
            .reduce("", (left, right) -> left + "\n" + right);
    }
}
