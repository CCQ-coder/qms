UPDATE qms_work_order
SET last_warehouse_date = DATE '2026-09-03'
WHERE tenant_code = 'demo-pcb'
  AND work_order_no = 'WO-260902-002'
  AND last_warehouse_date IS NULL;
