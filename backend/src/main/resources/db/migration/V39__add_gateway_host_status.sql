CREATE TABLE qms_gateway_host_status (
    tenant_code VARCHAR(64) NOT NULL,
    host_name VARCHAR(160) NOT NULL,
    sampled_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    cpu_percent NUMERIC(5, 2),
    memory_total_bytes BIGINT NOT NULL,
    memory_available_bytes BIGINT NOT NULL,
    system_disk_total_bytes BIGINT NOT NULL,
    system_disk_free_bytes BIGINT NOT NULL,
    uptime_seconds BIGINT NOT NULL,
    PRIMARY KEY (tenant_code, host_name)
);
