-- V39 seeded tenants existing at that migration. Backfill tenants created afterward.
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
  AND template.created_by = 'system.flying-probe'
  AND template.status = 'ACTIVE'
  AND template.updated_at = template.created_at
  AND NOT EXISTS (
      SELECT 1 FROM qms_inspection_template_item existing
      WHERE existing.template_id = template.id AND existing.item_code = item.item_code
  );
