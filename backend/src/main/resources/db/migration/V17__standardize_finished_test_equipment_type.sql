-- 将既有 FQC 电测设备切换为固定类型，确保升级后仍展示在成品测试实时监控中。
UPDATE qms_equipment
SET equipment_type = '成品测试设备', updated_at = CURRENT_TIMESTAMP
WHERE equipment_type = '成品电测设备';

UPDATE qms_administration_item
SET description = '成品测试设备日常点检项目。',
    attributes_json = '{"maintenanceType":"INSPECTION","equipmentType":"成品测试设备","cycle":"每日","items":"外观检查,接地检查,测试确认"}',
    updated_at = CURRENT_TIMESTAMP
WHERE tenant_code = 'demo-pcb'
  AND catalog_type = 'EQUIPMENT_TEMPLATE'
  AND item_code = 'FQC_DAILY_INSPECTION';
