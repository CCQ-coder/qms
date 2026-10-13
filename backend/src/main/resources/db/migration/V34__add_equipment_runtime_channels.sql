-- One physical machine can report several concurrently running engineering files.
-- Keep each field source's latest state independently instead of replacing the
-- equipment's single runtime row on every alternating report.
CREATE TABLE qms_equipment_runtime_channel (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL REFERENCES qms_equipment(id),
    source_name VARCHAR(160) NOT NULL,
    reported_status VARCHAR(32),
    program_no VARCHAR(160),
    operator_name VARCHAR(120),
    progress_percent NUMERIC(7, 3),
    spindle_status VARCHAR(48),
    alarm_code VARCHAR(80),
    alarm_message VARCHAR(1000),
    machining_count INTEGER,
    last_device_time TIMESTAMP NOT NULL,
    last_received_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_runtime_channel_source UNIQUE (tenant_code, equipment_id, source_name)
);
CREATE INDEX idx_equipment_runtime_channel_equipment
    ON qms_equipment_runtime_channel (tenant_code, equipment_id, last_received_at DESC);
