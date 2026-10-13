-- Keep the high-volume, second-level stream short-lived.  Summary rows are
-- maintained while ingesting, so historical trends survive raw-data cleanup.
CREATE INDEX idx_equipment_telemetry_tenant_received
    ON qms_equipment_telemetry (tenant_code, received_at DESC, id DESC);
CREATE INDEX idx_equipment_telemetry_received
    ON qms_equipment_telemetry (received_at);
CREATE INDEX idx_equipment_telemetry_tenant_equipment_received_id
    ON qms_equipment_telemetry (tenant_code, equipment_id, received_at DESC, id DESC);

CREATE TABLE qms_equipment_telemetry_minute_summary (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL,
    bucket_start TIMESTAMP NOT NULL,
    first_device_time TIMESTAMP NOT NULL,
    last_device_time TIMESTAMP NOT NULL,
    last_received_at TIMESTAMP NOT NULL,
    sample_count INTEGER NOT NULL,
    last_event_type VARCHAR(48),
    last_equipment_status VARCHAR(32),
    last_program_no VARCHAR(160),
    last_operator_name VARCHAR(120),
    max_progress_percent NUMERIC(7, 3),
    last_spindle_status VARCHAR(48),
    last_alarm_code VARCHAR(80),
    last_alarm_message VARCHAR(1000),
    last_machining_count INTEGER,
    max_machining_count INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_telemetry_minute_bucket UNIQUE (tenant_code, equipment_id, bucket_start)
);
CREATE INDEX idx_equipment_telemetry_minute_tenant_bucket
    ON qms_equipment_telemetry_minute_summary (tenant_code, bucket_start DESC);

CREATE TABLE qms_equipment_telemetry_hour_summary (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL,
    bucket_start TIMESTAMP NOT NULL,
    first_device_time TIMESTAMP NOT NULL,
    last_device_time TIMESTAMP NOT NULL,
    last_received_at TIMESTAMP NOT NULL,
    sample_count INTEGER NOT NULL,
    last_event_type VARCHAR(48),
    last_equipment_status VARCHAR(32),
    last_program_no VARCHAR(160),
    last_operator_name VARCHAR(120),
    max_progress_percent NUMERIC(7, 3),
    last_spindle_status VARCHAR(48),
    last_alarm_code VARCHAR(80),
    last_alarm_message VARCHAR(1000),
    last_machining_count INTEGER,
    max_machining_count INTEGER,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_telemetry_hour_bucket UNIQUE (tenant_code, equipment_id, bucket_start)
);
CREATE INDEX idx_equipment_telemetry_hour_tenant_bucket
    ON qms_equipment_telemetry_hour_summary (tenant_code, bucket_start DESC);
