CREATE TABLE qms_inspection_rule (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    product_code VARCHAR(80),
    process_code VARCHAR(80) NOT NULL,
    process_name VARCHAR(120) NOT NULL,
    task_type VARCHAR(24) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 100,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_inspection_rule_tenant_process ON qms_inspection_rule (tenant_code, process_code, enabled, priority);

INSERT INTO qms_inspection_rule (
    id, tenant_code, name, product_code, process_code, process_name, task_type, enabled, priority, created_by, created_at, updated_at
) VALUES
  ('f567a934-72fb-4c3f-9b24-000000000001', 'demo-pcb', 'FQC 成品检验默认规则', NULL, 'FQC', '成品检验', 'FINISHED', TRUE, 10, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('f567a934-72fb-4c3f-9b24-000000000002', 'demo-pcb', '阻焊制程巡检默认规则', NULL, 'SOLDER_MASK', '阻焊', 'PROCESS', TRUE, 20, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

