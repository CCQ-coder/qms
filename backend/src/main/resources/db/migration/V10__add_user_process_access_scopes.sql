CREATE TABLE qms_user_access_profile (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    user_name VARCHAR(120) NOT NULL,
    display_name VARCHAR(120),
    role_code VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_access_profile_tenant_user UNIQUE (tenant_code, user_name)
);
CREATE INDEX idx_user_access_profile_tenant_role ON qms_user_access_profile (tenant_code, role_code, status);

CREATE TABLE qms_user_process_scope (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    user_name VARCHAR(120) NOT NULL,
    process_code VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_process_scope UNIQUE (tenant_code, user_name, process_code)
);
CREATE INDEX idx_user_process_scope_tenant_user ON qms_user_process_scope (tenant_code, user_name);

INSERT INTO qms_user_access_profile (id, tenant_code, user_name, display_name, role_code, status, created_at, updated_at) VALUES
  ('fe67a934-72fb-4c3f-9b24-000000000001', 'demo-pcb', 'demo.admin', '演示管理员', 'TENANT_ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('fe67a934-72fb-4c3f-9b24-000000000002', 'demo-pcb', 'demo.inspector', '演示检验员', 'INSPECTOR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('fe67a934-72fb-4c3f-9b24-000000000003', 'demo-pcb', 'demo.supervisor', '演示主管', 'SUPERVISOR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qms_user_process_scope (id, tenant_code, user_name, process_code, created_at) VALUES
  ('ff67a934-72fb-4c3f-9b24-000000000001', 'demo-pcb', 'demo.inspector', 'FQC', CURRENT_TIMESTAMP);
