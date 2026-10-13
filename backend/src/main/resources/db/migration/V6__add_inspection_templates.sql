CREATE TABLE qms_inspection_template (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    template_code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    product_code VARCHAR(80),
    process_code VARCHAR(80) NOT NULL,
    process_name VARCHAR(120) NOT NULL,
    task_type VARCHAR(24) NOT NULL,
    version_no INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_inspection_template_tenant_code UNIQUE (tenant_code, template_code)
);
CREATE INDEX idx_inspection_template_tenant_process ON qms_inspection_template (tenant_code, process_code, status);

CREATE TABLE qms_inspection_template_item (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    template_id VARCHAR(36) NOT NULL REFERENCES qms_inspection_template(id),
    sort_order INTEGER NOT NULL,
    item_code VARCHAR(80) NOT NULL,
    item_name VARCHAR(160) NOT NULL,
    input_type VARCHAR(24) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    lower_limit DECIMAL(18,6),
    upper_limit DECIMAL(18,6),
    unit VARCHAR(40),
    options_json TEXT,
    help_text VARCHAR(500),
    CONSTRAINT uq_inspection_template_item_code UNIQUE (template_id, item_code)
);
CREATE INDEX idx_inspection_template_item_template ON qms_inspection_template_item (tenant_code, template_id, sort_order);

ALTER TABLE qms_inspection_rule ADD COLUMN template_id VARCHAR(36) REFERENCES qms_inspection_template(id);
ALTER TABLE qms_inspection_task ADD COLUMN template_id VARCHAR(36) REFERENCES qms_inspection_template(id);

INSERT INTO qms_inspection_template (
    id, tenant_code, template_code, name, product_code, process_code, process_name, task_type,
    version_no, status, created_by, created_at, updated_at
) VALUES
  ('fa67a934-72fb-4c3f-9b24-000000000001', 'demo-pcb', 'FQC-DEFAULT-V1', 'FQC 成品检验默认模板', NULL, 'FQC', '成品检验', 'FINISHED', 1, 'ACTIVE', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('fa67a934-72fb-4c3f-9b24-000000000002', 'demo-pcb', 'SM-PROCESS-V1', '阻焊制程巡检默认模板', NULL, 'SOLDER_MASK', '阻焊', 'PROCESS', 1, 'ACTIVE', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qms_inspection_template_item (
    id, tenant_code, template_id, sort_order, item_code, item_name, input_type, is_required,
    lower_limit, upper_limit, unit, options_json, help_text
) VALUES
  ('fb67a934-72fb-4c3f-9b24-000000000001', 'demo-pcb', 'fa67a934-72fb-4c3f-9b24-000000000001', 10, 'appearance', '外观检查', 'SELECT', TRUE, NULL, NULL, NULL, '["合格","不合格"]', '检查划伤、露铜、异物与字符清晰度'),
  ('fb67a934-72fb-4c3f-9b24-000000000002', 'demo-pcb', 'fa67a934-72fb-4c3f-9b24-000000000001', 20, 'board_thickness', '板厚', 'NUMBER', TRUE, 1.500000, 1.700000, 'mm', NULL, '按客户规格测量'),
  ('fb67a934-72fb-4c3f-9b24-000000000003', 'demo-pcb', 'fa67a934-72fb-4c3f-9b24-000000000001', 30, 'continuity', '导通测试', 'SELECT', TRUE, NULL, NULL, NULL, '["合格","不合格"]', '记录电测结果'),
  ('fb67a934-72fb-4c3f-9b24-000000000004', 'demo-pcb', 'fa67a934-72fb-4c3f-9b24-000000000002', 10, 'ink_color', '油墨颜色与覆盖', 'SELECT', TRUE, NULL, NULL, NULL, '["合格","不合格"]', '检查阻焊颜色、覆盖和针孔'),
  ('fb67a934-72fb-4c3f-9b24-000000000005', 'demo-pcb', 'fa67a934-72fb-4c3f-9b24-000000000002', 20, 'registration', '对位偏差', 'NUMBER', TRUE, 0.000000, 0.075000, 'mm', NULL, '按制程控制要求测量');

UPDATE qms_inspection_rule
SET template_id = 'fa67a934-72fb-4c3f-9b24-000000000001'
WHERE id = 'f567a934-72fb-4c3f-9b24-000000000001';

UPDATE qms_inspection_rule
SET template_id = 'fa67a934-72fb-4c3f-9b24-000000000002'
WHERE id = 'f567a934-72fb-4c3f-9b24-000000000002';
