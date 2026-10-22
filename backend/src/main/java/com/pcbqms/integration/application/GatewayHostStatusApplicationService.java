package com.pcbqms.integration.application;

import com.pcbqms.common.tenancy.TenantContext;
import com.pcbqms.integration.api.GatewayHostStatusRequest;
import com.pcbqms.integration.api.GatewayHostStatusResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GatewayHostStatusApplicationService {
    private final JdbcTemplate jdbcTemplate;

    public GatewayHostStatusApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(GatewayHostStatusRequest request) {
        String hostName = request == null || request.hostName() == null ? null
            : request.hostName().trim().toUpperCase(Locale.ROOT);
        if (request == null || !StringUtils.hasText(hostName) || hostName.length() > 160
            || request.sampledAt() == null || request.cpuPercent() != null
                && (request.cpuPercent().signum() < 0 || request.cpuPercent().compareTo(java.math.BigDecimal.valueOf(100)) > 0)
            || request.memoryTotalBytes() == null || request.memoryTotalBytes() <= 0
            || request.memoryAvailableBytes() == null || request.memoryAvailableBytes() < 0
            || request.memoryAvailableBytes() > request.memoryTotalBytes()
            || request.systemDiskTotalBytes() == null || request.systemDiskTotalBytes() <= 0
            || request.systemDiskFreeBytes() == null || request.systemDiskFreeBytes() < 0
            || request.systemDiskFreeBytes() > request.systemDiskTotalBytes()
            || request.uptimeSeconds() == null || request.uptimeSeconds() < 0) {
            throw new IllegalArgumentException("无效的电脑状态");
        }
        jdbcTemplate.update("""
            INSERT INTO qms_gateway_host_status (
                tenant_code, host_name, sampled_at, received_at, cpu_percent,
                memory_total_bytes, memory_available_bytes, system_disk_total_bytes,
                system_disk_free_bytes, uptime_seconds
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                sampled_at = VALUES(sampled_at), received_at = VALUES(received_at),
                cpu_percent = VALUES(cpu_percent), memory_total_bytes = VALUES(memory_total_bytes),
                memory_available_bytes = VALUES(memory_available_bytes),
                system_disk_total_bytes = VALUES(system_disk_total_bytes),
                system_disk_free_bytes = VALUES(system_disk_free_bytes), uptime_seconds = VALUES(uptime_seconds)
            """, TenantContext.tenantCode(), hostName,
            Timestamp.from(request.sampledAt().toInstant()), Timestamp.from(Instant.now()), request.cpuPercent(),
            request.memoryTotalBytes(), request.memoryAvailableBytes(), request.systemDiskTotalBytes(),
            request.systemDiskFreeBytes(), request.uptimeSeconds());
    }

    public List<GatewayHostStatusResponse> listLatest() {
        return jdbcTemplate.query("""
            SELECT host.host_name, host.sampled_at, host.received_at, host.cpu_percent,
                   host.memory_total_bytes, host.memory_available_bytes,
                   host.system_disk_total_bytes, host.system_disk_free_bytes, host.uptime_seconds,
                   equipment.id AS equipment_id, equipment.asset_no AS equipment_code,
                   equipment.name AS equipment_name
            FROM qms_gateway_host_status host
            LEFT JOIN qms_equipment_gateway_mapping mapping
                ON mapping.tenant_code = host.tenant_code AND mapping.machine_name = host.host_name
            LEFT JOIN qms_equipment equipment
                ON equipment.tenant_code = host.tenant_code AND equipment.id = mapping.equipment_id
            WHERE host.tenant_code = ? ORDER BY host.host_name
            """, (row, index) -> new GatewayHostStatusResponse(
                row.getString("host_name"), row.getTimestamp("sampled_at").toInstant(),
                row.getBigDecimal("cpu_percent"), row.getLong("memory_total_bytes"),
                row.getLong("memory_available_bytes"), row.getLong("system_disk_total_bytes"),
                row.getLong("system_disk_free_bytes"), row.getLong("uptime_seconds"),
                row.getTimestamp("received_at").toInstant(), row.getString("equipment_id"),
                row.getString("equipment_code"), row.getString("equipment_name")), TenantContext.tenantCode());
    }
}
