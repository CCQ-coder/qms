CREATE TABLE qms_product (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    product_code VARCHAR(80) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    specification VARCHAR(500),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_product_tenant_code UNIQUE (tenant_code, product_code)
);
CREATE INDEX idx_product_tenant_status ON qms_product (tenant_code, status, product_code);

CREATE TABLE qms_process (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    process_code VARCHAR(80) NOT NULL,
    process_name VARCHAR(120) NOT NULL,
    sequence_no INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_process_tenant_code UNIQUE (tenant_code, process_code)
);
CREATE INDEX idx_process_tenant_status ON qms_process (tenant_code, status, sequence_no);

INSERT INTO qms_product (
    id, tenant_code, product_code, product_name, specification, status, created_by, created_at, updated_at
) VALUES
  ('fc67a934-72fb-4c3f-9b24-000000000001', 'demo-pcb', 'PCB-DEMO-01', '四层通孔演示板', 'FR-4 / 1.60 mm / 四层', 'ACTIVE', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qms_process (
    id, tenant_code, process_code, process_name, sequence_no, status, created_by, created_at, updated_at
) VALUES
  ('fd67a934-72fb-4c3f-9b24-000000000001', 'demo-pcb', 'SOLDER_MASK', '阻焊', 70, 'ACTIVE', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('fd67a934-72fb-4c3f-9b24-000000000002', 'demo-pcb', 'FQC', '成品检验', 100, 'ACTIVE', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
