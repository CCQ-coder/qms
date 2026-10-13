CREATE TABLE qms_equipment_gateway_unmatched_message (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    source_key VARCHAR(200) NOT NULL,
    source_username VARCHAR(160),
    machine_name VARCHAR(160),
    last_device_time TIMESTAMP NULL,
    last_received_at TIMESTAMP NOT NULL,
    occurrence_count INT NOT NULL DEFAULT 1,
    raw_payload MEDIUMTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_gateway_unmatched_source UNIQUE (tenant_code, source_key)
);

CREATE INDEX idx_equipment_gateway_unmatched_recent
    ON qms_equipment_gateway_unmatched_message (tenant_code, last_received_at DESC);
