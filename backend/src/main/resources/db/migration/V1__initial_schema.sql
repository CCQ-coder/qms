CREATE TABLE qms_tenant (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE qms_work_order (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    work_order_no VARCHAR(80) NOT NULL,
    product_code VARCHAR(80) NOT NULL,
    product_name VARCHAR(160),
    specification VARCHAR(300),
    current_process_code VARCHAR(80),
    current_process_name VARCHAR(120),
    planned_quantity INTEGER NOT NULL DEFAULT 0,
    completed_quantity INTEGER NOT NULL DEFAULT 0,
    source_system VARCHAR(40) NOT NULL,
    source_key VARCHAR(120),
    source_version VARCHAR(120),
    source_payload_hash VARCHAR(128),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_work_order_tenant_no UNIQUE (tenant_code, work_order_no)
);
CREATE INDEX idx_work_order_tenant_updated ON qms_work_order (tenant_code, updated_at DESC);

CREATE TABLE qms_production_batch (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    work_order_id VARCHAR(36) NOT NULL REFERENCES qms_work_order(id),
    batch_no VARCHAR(80) NOT NULL,
    planned_quantity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_batch_tenant_no UNIQUE (tenant_code, batch_no)
);
CREATE INDEX idx_batch_tenant_work_order ON qms_production_batch (tenant_code, work_order_id);

CREATE TABLE qms_inspection_task (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    task_no VARCHAR(80) NOT NULL,
    work_order_id VARCHAR(36) NOT NULL REFERENCES qms_work_order(id),
    batch_id VARCHAR(36) REFERENCES qms_production_batch(id),
    process_code VARCHAR(80) NOT NULL,
    process_name VARCHAR(120) NOT NULL,
    task_type VARCHAR(24) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    assignee_name VARCHAR(120),
    due_at TIMESTAMP,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_inspection_task_tenant_no UNIQUE (tenant_code, task_no)
);
CREATE INDEX idx_inspection_task_tenant_status ON qms_inspection_task (tenant_code, status, created_at DESC);

CREATE TABLE qms_inspection_record (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    task_id VARCHAR(36) NOT NULL REFERENCES qms_inspection_task(id),
    verdict VARCHAR(12) NOT NULL,
    defective_quantity INTEGER NOT NULL DEFAULT 0,
    defect_summary VARCHAR(500),
    results_json TEXT,
    attachment_urls_json TEXT,
    inspected_by VARCHAR(120) NOT NULL,
    inspected_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_inspection_record_tenant_task ON qms_inspection_record (tenant_code, task_id);

CREATE TABLE qms_nonconformity (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    ncr_no VARCHAR(80) NOT NULL,
    inspection_record_id VARCHAR(36) NOT NULL REFERENCES qms_inspection_record(id),
    work_order_id VARCHAR(36) NOT NULL REFERENCES qms_work_order(id),
    batch_id VARCHAR(36) REFERENCES qms_production_batch(id),
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    initiated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_ncr_tenant_no UNIQUE (tenant_code, ncr_no)
);

CREATE TABLE qms_system_parameter (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    parameter_key VARCHAR(120) NOT NULL,
    parameter_value VARCHAR(1000) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_system_parameter_tenant_key UNIQUE (tenant_code, parameter_key)
);

