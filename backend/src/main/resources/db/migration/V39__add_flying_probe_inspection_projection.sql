-- Keep the source and conversion decision after raw telemetry's short retention window.
CREATE TABLE qms_flying_probe_projection (
    telemetry_id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL,
    machine_name VARCHAR(160),
    material_no VARCHAR(160),
    device_time TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    source_record_no VARCHAR(160),
    source_file VARCHAR(512),
    source_start_time TIMESTAMP NULL,
    run_key CHAR(64),
    cumulative_tested INTEGER,
    cumulative_good INTEGER,
    cumulative_bad INTEGER,
    delta_tested INTEGER,
    delta_good INTEGER,
    delta_bad INTEGER,
    work_order_no VARCHAR(80),
    batch_no VARCHAR(80),
    inspection_task_id VARCHAR(36),
    inspection_record_id VARCHAR(36),
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(500),
    raw_payload JSON NOT NULL,
    retry_after TIMESTAMP NULL,
    processed_at TIMESTAMP NOT NULL,
    INDEX idx_flying_probe_projection_retry (tenant_code, status, retry_after, device_time),
    INDEX idx_flying_probe_projection_run (tenant_code, equipment_id, run_key, device_time)
);

CREATE TABLE qms_flying_probe_run (
    run_key CHAR(64) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    equipment_id VARCHAR(36) NOT NULL,
    source_file VARCHAR(512) NOT NULL,
    source_start_time TIMESTAMP NOT NULL,
    material_no VARCHAR(160) NOT NULL,
    work_order_no VARCHAR(80) NOT NULL,
    last_device_time TIMESTAMP NULL,
    tested_count INTEGER NOT NULL DEFAULT 0,
    good_count INTEGER NOT NULL DEFAULT 0,
    bad_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    reason VARCHAR(500),
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_flying_probe_run_equipment (tenant_code, equipment_id, source_start_time)
);

CREATE INDEX idx_equipment_telemetry_projection_order
    ON qms_equipment_telemetry (tenant_code, event_type, device_time, received_at, id);

CREATE INDEX idx_inspection_record_finished_page
    ON qms_inspection_record (tenant_code, inspected_at DESC, id DESC);

-- This standard covers electrical test counts only. Other FQC checks remain separate.
INSERT INTO qms_inspection_template (
    id, tenant_code, template_code, name, product_code, process_code, process_name, task_type,
    version_no, status, created_by, created_at, updated_at
)
SELECT UUID(), tenant.code, 'FLYING_PROBE_ELECTRICAL_V1', '飞针电测增量检验标准', NULL,
       'FQC', '成品检验', 'FINISHED', 1, 'ACTIVE', 'system.flying-probe', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM qms_tenant tenant
WHERE NOT EXISTS (
    SELECT 1 FROM qms_inspection_template template
    WHERE template.tenant_code = tenant.code AND template.template_code = 'FLYING_PROBE_ELECTRICAL_V1'
);

INSERT INTO qms_inspection_template_item (
    id, tenant_code, template_id, sort_order, item_code, item_name, input_type, is_required,
    lower_limit, upper_limit, unit, options_json, help_text
)
SELECT UUID(), template.tenant_code, template.id, item.sort_order, item.item_code, item.item_name,
       'NUMBER', TRUE, item.lower_limit, item.upper_limit, '板', NULL, item.help_text
FROM qms_inspection_template template
JOIN (
    SELECT 10 AS sort_order, 'testedBoardCount' AS item_code, '本次新增已测板数' AS item_name,
           1 AS lower_limit, NULL AS upper_limit, '以相邻有效累计快照之差计算' AS help_text
    UNION ALL
    SELECT 20, 'goodBoardCount', '本次新增好板数', 0, NULL, '以相邻有效累计快照之差计算'
    UNION ALL
    SELECT 30, 'badBoardCount', '本次新增坏板数', 0, 0, '本次坏板为 0 才可判 PASS，否则 FAIL'
) item
WHERE template.template_code = 'FLYING_PROBE_ELECTRICAL_V1'
  AND NOT EXISTS (
      SELECT 1 FROM qms_inspection_template_item existing
      WHERE existing.template_id = template.id AND existing.item_code = item.item_code
  );
