CREATE TABLE qms_administration_item (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    catalog_type VARCHAR(40) NOT NULL,
    item_code VARCHAR(120) NOT NULL,
    item_name VARCHAR(160) NOT NULL,
    parent_code VARCHAR(120),
    description VARCHAR(1000),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    sort_order INTEGER NOT NULL DEFAULT 100,
    attributes_json VARCHAR(4000) NOT NULL DEFAULT '{}',
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_administration_item_tenant_type_code UNIQUE (tenant_code, catalog_type, item_code)
);
CREATE INDEX idx_administration_item_tenant_type ON qms_administration_item (tenant_code, catalog_type, status, sort_order);

CREATE TABLE qms_administration_audit_log (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    module_code VARCHAR(40) NOT NULL,
    operation VARCHAR(24) NOT NULL,
    target_code VARCHAR(120) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    actor VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_administration_audit_tenant_created ON qms_administration_audit_log (tenant_code, created_at DESC);

INSERT INTO qms_administration_item (
    id, tenant_code, catalog_type, item_code, item_name, parent_code, description, status, sort_order,
    attributes_json, created_by, updated_by, created_at, updated_at
) VALUES
    ('14000000-0000-4000-8000-000000000001', 'demo-pcb', 'ROLE', 'TENANT_ADMIN', '租户管理员', NULL, '管理本租户全部配置与质量数据。', 'ACTIVE', 10, '{"dataScope":"全部数据","permissions":"全部菜单、参数与配置"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('14000000-0000-4000-8000-000000000002', 'demo-pcb', 'ROLE', 'SUPERVISOR', '质量主管', NULL, '负责质量分析、处置与复核。', 'ACTIVE', 20, '{"dataScope":"全部质量数据","permissions":"总览、检测、追溯与报表"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('14000000-0000-4000-8000-000000000003', 'demo-pcb', 'ROLE', 'INSPECTOR', 'FQC 检验员', NULL, '执行本工序的成品检验与不良反馈。', 'ACTIVE', 30, '{"dataScope":"已分配工序","permissions":"成品测试、任务与追溯"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('14000000-0000-4000-8000-000000000004', 'demo-pcb', 'ORGANIZATION', 'PCB-FACTORY', '演示 PCB 工厂', NULL, '质量管理租户的生产组织。', 'ACTIVE', 10, '{"organizationType":"工厂","responsiblePerson":"演示管理员"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('14000000-0000-4000-8000-000000000005', 'demo-pcb', 'DEPARTMENT', 'QUALITY', '质量部', 'PCB-FACTORY', '负责成品测试与质量闭环。', 'ACTIVE', 10, '{"manager":"质量主管"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('14000000-0000-4000-8000-000000000006', 'demo-pcb', 'SCRAP_REASON', 'OPEN_CIRCUIT', '开路不良', NULL, '线路开路导致测试不通过。', 'ACTIVE', 10, '{"category":"电气不良"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('14000000-0000-4000-8000-000000000007', 'demo-pcb', 'DICTIONARY', 'DEFECT_CATEGORY', '缺陷分类', NULL, '成品检验缺陷分类字典。', 'ACTIVE', 10, '{"dataType":"业务字典","values":"开路,短路,外观不良"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('14000000-0000-4000-8000-000000000008', 'demo-pcb', 'INTERFACE', 'ERP_GATEWAY', 'ERP 工单接口', NULL, '接收 ERP 下发的生产指令。', 'ACTIVE', 10, '{"endpoint":"/api/v1/gateway/work-orders","authType":"API Key","syncMode":"实时"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('14000000-0000-4000-8000-000000000009', 'demo-pcb', 'APPROVAL_FLOW', 'NCR_DISPOSITION', '不良品处置审批', NULL, '不良品处置的质量主管审批流程。', 'ACTIVE', 10, '{"businessType":"不良品处置","approverRole":"SUPERVISOR","trigger":"提交处置"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('14000000-0000-4000-8000-000000000010', 'demo-pcb', 'EQUIPMENT_TEMPLATE', 'FQC_DAILY_INSPECTION', 'FQC 电测设备日点检', NULL, '成品测试设备日常点检项目。', 'ACTIVE', 10, '{"maintenanceType":"INSPECTION","equipmentType":"成品测试设备","cycle":"每日","items":"外观检查,接地检查,测试确认"}', 'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qms_administration_audit_log (id, tenant_code, module_code, operation, target_code, summary, actor, created_at) VALUES
    ('14000000-0000-4000-8000-000000000101', 'demo-pcb', 'SYSTEM', 'INITIALIZE', 'ADMINISTRATION', '已初始化管理配置中心的演示数据', 'system', CURRENT_TIMESTAMP);
