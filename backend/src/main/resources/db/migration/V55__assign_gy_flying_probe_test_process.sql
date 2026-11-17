UPDATE qms_equipment
SET process_code = '004', process_name = '测试'
WHERE tenant_code = 'customer-b'
  AND asset_no IN ('WTD-1', 'WTD-2', 'ZBX-1', 'ZBX-2', 'ZBX-3', 'ZBX-4')
  AND (process_code IS NULL OR TRIM(process_code) = '');
