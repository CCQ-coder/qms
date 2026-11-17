UPDATE qms_equipment
SET process_code = '001', process_name = '成品测试'
WHERE tenant_code = 'customer-a'
  AND equipment_type = '成品测试设备'
  AND asset_no IN (
    'WTD-1', 'WTD-2', 'WTD-3', 'WTD-4', 'WTD-5', 'WTD-6', 'WTD-7', 'WTD-8', 'WTD-9',
    'WTD-10', 'WTD-11', 'WTD-12', 'WTD-13', 'WTD-14', 'WTD-15', 'WTD-16', 'WTD-17', 'WTD-18', 'WTD-19',
    'WTD-20', 'WTD-21', 'WTD-22', 'WTD-23', 'WTD-24', 'WTD-25', 'WTD-26', 'WTD-27', 'WTD-28', 'WTD-29'
  )
  AND (process_code IS NULL OR TRIM(process_code) = '');
