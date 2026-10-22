package com.pcbqms.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pcbqms.common.tenancy.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcbqms.integration.api.GatewayHostStatusRequest;
import com.pcbqms.integration.api.GatewayHostStatusResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class GatewayHostStatusApplicationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final GatewayHostStatusApplicationService service = new GatewayHostStatusApplicationService(jdbc);

    @BeforeEach
    void tenant() {
        TenantContext.set("customer-b", "test");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void recordRejectsImpossibleMetrics() {
        assertThatThrownBy(() -> service.record(sample(101L, 100L, 40L, 80L)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(sample(50L, 100L, -1L, 80L)))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void recordRejectsHostThatExpandsBeyondColumnLength() {
        GatewayHostStatusRequest request = new GatewayHostStatusRequest("ß".repeat(160),
            OffsetDateTime.of(2026, 10, 22, 5, 0, 0, 0, ZoneOffset.UTC),
            null, 100L, 50L, 80L, 40L, 3600L);

        assertThatThrownBy(() -> service.record(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void upsertUsesTenantAndNormalizedHost() {
        service.record(sample(50L, 100L, 40L, 80L));

        verify(jdbc).update(
            org.mockito.ArgumentMatchers.contains("ON DUPLICATE KEY UPDATE"),
            eq("customer-b"), eq("SCBGX"),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), eq(100L), eq(50L), eq(80L), eq(40L),
            eq(3600L));
    }

    @Test
    void listLatestScopesTenantAndMapsReceivedTime() throws Exception {
        ResultSet row = mock(ResultSet.class);
        Instant sampled = Instant.parse("2026-10-22T05:00:00Z");
        Instant received = Instant.parse("2026-10-22T05:00:03Z");
        when(row.getString("host_name")).thenReturn("SCBGX");
        when(row.getString("equipment_id")).thenReturn("equipment-scbgx");
        when(row.getString("equipment_code")).thenReturn("SCBGX");
        when(row.getString("equipment_name")).thenReturn("飞针机采集电脑");
        when(row.getTimestamp("sampled_at")).thenReturn(Timestamp.from(sampled));
        when(row.getTimestamp("received_at")).thenReturn(Timestamp.from(received));
        when(row.getBigDecimal("cpu_percent")).thenReturn(new BigDecimal("12.5"));
        when(row.getLong("memory_total_bytes")).thenReturn(100L);
        when(row.getLong("memory_available_bytes")).thenReturn(50L);
        when(row.getLong("system_disk_total_bytes")).thenReturn(80L);
        when(row.getLong("system_disk_free_bytes")).thenReturn(40L);
        when(row.getLong("uptime_seconds")).thenReturn(3600L);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<GatewayHostStatusResponse>>any(),
            eq("customer-b"))).thenAnswer(call -> {
                RowMapper<GatewayHostStatusResponse> mapper = call.getArgument(1);
                return List.of(mapper.mapRow(row, 0));
            });

        List<GatewayHostStatusResponse> latest = service.listLatest();

        assertThat(latest).hasSize(1);
        assertThat(latest.get(0).hostName()).isEqualTo("SCBGX");
        assertThat(latest.get(0).sampledAt()).isEqualTo(sampled);
        assertThat(latest.get(0).receivedAt()).isEqualTo(received);
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(latest.get(0));
        assertThat(json.path("equipmentId").asText()).isEqualTo("equipment-scbgx");
        assertThat(json.path("equipmentCode").asText()).isEqualTo("SCBGX");
        assertThat(json.path("equipmentName").asText()).isEqualTo("飞针机采集电脑");
        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<GatewayHostStatusResponse>>any(),
            eq("customer-b"));
    }

    private GatewayHostStatusRequest sample(Long memoryAvailable, Long memoryTotal,
                                            Long diskFree, Long diskTotal) {
        return new GatewayHostStatusRequest(" scbgx ",
            OffsetDateTime.of(2026, 10, 22, 5, 0, 0, 0, ZoneOffset.UTC),
            new BigDecimal("12.5"), memoryTotal, memoryAvailable, diskTotal, diskFree, 3600L);
    }
}
