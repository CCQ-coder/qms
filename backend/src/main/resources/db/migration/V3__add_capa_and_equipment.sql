CREATE TABLE qms_capa (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    capa_no VARCHAR(80) NOT NULL,
    nonconformity_id VARCHAR(36) NOT NULL REFERENCES qms_nonconformity(id),
    problem_description TEXT NOT NULL,
    root_cause TEXT,
    corrective_action TEXT,
    verification_result TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    initiated_by VARCHAR(120) NOT NULL,
    approved_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    CONSTRAINT uq_capa_tenant_no UNIQUE (tenant_code, capa_no)
);
CREATE INDEX idx_capa_tenant_status ON qms_capa (tenant_code, status, created_at DESC);

CREATE TABLE qms_equipment (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    asset_no VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    equipment_type VARCHAR(120) NOT NULL,
    process_code VARCHAR(80),
    process_name VARCHAR(120),
    status VARCHAR(24) NOT NULL DEFAULT 'IN_USE',
    responsible_name VARCHAR(120),
    calibration_due_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_tenant_asset UNIQUE (tenant_code, asset_no)
);
CREATE INDEX idx_equipment_tenant_status ON qms_equipment (tenant_code, status);

CREATE TABLE qms_equipment_maintenance (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    record_no VARCHAR(80) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL REFERENCES qms_equipment(id),
    maintenance_type VARCHAR(24) NOT NULL,
    scheduled_at TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    description TEXT NOT NULL,
    result TEXT,
    executor_name VARCHAR(120),
    verifier_name VARCHAR(120),
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_maintenance_tenant_no UNIQUE (tenant_code, record_no)
);
CREATE INDEX idx_equipment_maintenance_tenant_status ON qms_equipment_maintenance (tenant_code, status, scheduled_at);

