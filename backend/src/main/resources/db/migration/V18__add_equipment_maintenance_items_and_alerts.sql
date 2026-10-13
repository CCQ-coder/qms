CREATE TABLE qms_equipment_maintenance_template_item (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_type VARCHAR(120) NOT NULL,
    maintenance_type VARCHAR(24) NOT NULL,
    item_code VARCHAR(80) NOT NULL,
    item_name VARCHAR(160) NOT NULL,
    lower_limit NUMERIC(14,4),
    upper_limit NUMERIC(14,4),
    unit VARCHAR(32),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_template_item UNIQUE (tenant_code, equipment_type, maintenance_type, item_code)
);

CREATE TABLE qms_equipment_maintenance_item (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    maintenance_id VARCHAR(36) NOT NULL REFERENCES qms_equipment_maintenance(id),
    template_item_id VARCHAR(36),
    item_code VARCHAR(80) NOT NULL,
    item_name VARCHAR(160) NOT NULL,
    lower_limit NUMERIC(14,4),
    upper_limit NUMERIC(14,4),
    unit VARCHAR(32),
    measured_value NUMERIC(14,4),
    judgement VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_maintenance_item UNIQUE (maintenance_id, item_code)
);

CREATE TABLE qms_equipment_alert (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    alert_no VARCHAR(80) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL REFERENCES qms_equipment(id),
    maintenance_id VARCHAR(36) NOT NULL REFERENCES qms_equipment_maintenance(id),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    resolved_by_maintenance_id VARCHAR(36),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_equipment_alert_tenant_no UNIQUE (tenant_code, alert_no)
);

CREATE INDEX idx_equipment_template_type ON qms_equipment_maintenance_template_item (tenant_code, equipment_type, maintenance_type, status);
CREATE INDEX idx_equipment_maintenance_item_maintenance ON qms_equipment_maintenance_item (tenant_code, maintenance_id);
CREATE INDEX idx_equipment_alert_status ON qms_equipment_alert (tenant_code, equipment_id, status, created_at DESC);

INSERT INTO qms_equipment_maintenance_template_item (
    id, tenant_code, equipment_type, maintenance_type, item_code, item_name, lower_limit, upper_limit, unit, status, created_by, created_at, updated_at
) VALUES
    ('16000000-0000-4000-8000-000000000001', 'demo-pcb', '成品测试设备', 'INSPECTION', 'POWER_VOLTAGE', '供电电压', 198, 242, 'V', 'ACTIVE', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('16000000-0000-4000-8000-000000000002', 'demo-pcb', '成品测试设备', 'INSPECTION', 'GROUND_RESISTANCE', '接地电阻', 0, 4, 'Ω', 'ACTIVE', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('16000000-0000-4000-8000-000000000003', 'demo-pcb', '成品测试设备', 'MAINTENANCE', 'TEST_ACCURACY', '测试精度偏差', 0, 0.05, '%', 'ACTIVE', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
