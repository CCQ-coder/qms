-- QMS stores the gateway's normalized CNC messages. It deliberately has no
-- dependency on a machine vendor's TCP protocol or port.
CREATE TABLE qms_equipment_gateway_mapping (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL REFERENCES qms_equipment(id),
    source_username VARCHAR(160),
    machine_name VARCHAR(160),
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_equipment_gateway_mapping_identifier
        CHECK (source_username IS NOT NULL OR machine_name IS NOT NULL),
    CONSTRAINT uq_equipment_gateway_mapping_username UNIQUE (tenant_code, source_username),
    CONSTRAINT uq_equipment_gateway_mapping_machine UNIQUE (tenant_code, machine_name)
);
CREATE INDEX idx_equipment_gateway_mapping_equipment
    ON qms_equipment_gateway_mapping (tenant_code, equipment_id);

CREATE TABLE qms_equipment_telemetry (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL REFERENCES qms_equipment(id),
    source_username VARCHAR(160),
    machine_name VARCHAR(160),
    device_time TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    equipment_status VARCHAR(32),
    program_no VARCHAR(160),
    program_version VARCHAR(80),
    material_no VARCHAR(160),
    work_order_no VARCHAR(80),
    batch_no VARCHAR(80),
    operator_name VARCHAR(120),
    progress_percent NUMERIC(7, 3),
    spindle_status VARCHAR(48),
    alarm_code VARCHAR(80),
    alarm_message VARCHAR(1000),
    machining_count INTEGER,
    source_record_no VARCHAR(160),
    deduplication_key VARCHAR(512) NOT NULL,
    raw_payload JSON NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_telemetry_deduplication
        UNIQUE (tenant_code, equipment_id, deduplication_key)
);
CREATE INDEX idx_equipment_telemetry_equipment_received
    ON qms_equipment_telemetry (tenant_code, equipment_id, received_at DESC);
CREATE INDEX idx_equipment_telemetry_equipment_device_time
    ON qms_equipment_telemetry (tenant_code, equipment_id, device_time DESC);

CREATE TABLE qms_equipment_runtime_status (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL REFERENCES qms_equipment(id),
    reported_status VARCHAR(32),
    program_no VARCHAR(160),
    program_version VARCHAR(80),
    material_no VARCHAR(160),
    work_order_no VARCHAR(80),
    batch_no VARCHAR(80),
    operator_name VARCHAR(120),
    progress_percent NUMERIC(7, 3),
    spindle_status VARCHAR(48),
    alarm_code VARCHAR(80),
    alarm_message VARCHAR(1000),
    machining_count INTEGER,
    last_source_record_no VARCHAR(160),
    last_device_time TIMESTAMP NOT NULL,
    last_received_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_runtime_status UNIQUE (tenant_code, equipment_id)
);
CREATE INDEX idx_equipment_runtime_status_received
    ON qms_equipment_runtime_status (tenant_code, last_received_at DESC);

-- These are durable business-rule triggers.  A workflow, notification, or
-- MES adapter can consume PENDING records without coupling to device syntax.
CREATE TABLE qms_equipment_business_event (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL REFERENCES qms_equipment(id),
    telemetry_id VARCHAR(36) NOT NULL REFERENCES qms_equipment_telemetry(id),
    event_type VARCHAR(48) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    payload JSON NOT NULL,
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT uq_equipment_business_event_source UNIQUE (telemetry_id, event_type)
);
CREATE INDEX idx_equipment_business_event_pending
    ON qms_equipment_business_event (tenant_code, status, created_at);

-- A barcode may select a different approved program for each mapped machine.
CREATE TABLE qms_cnc_program_release (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL REFERENCES qms_equipment(id),
    barcode VARCHAR(200) NOT NULL,
    program_no VARCHAR(160) NOT NULL,
    program_name VARCHAR(200),
    program_version VARCHAR(80) NOT NULL,
    file_url VARCHAR(2000) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    file_size_bytes BIGINT,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_cnc_program_release_barcode UNIQUE (tenant_code, equipment_id, barcode)
);
CREATE INDEX idx_cnc_program_release_lookup
    ON qms_cnc_program_release (tenant_code, equipment_id, barcode, status);

-- A machine-originated alarm is not tied to a maintenance record. Retain the
-- existing maintenance foreign key when one is present, but permit null here.
ALTER TABLE qms_equipment_alert MODIFY COLUMN maintenance_id VARCHAR(36) NULL;
