ALTER TABLE qms_work_order
    ADD COLUMN material_input_quantity INTEGER,
    ADD COLUMN cumulative_scrap_quantity INTEGER,
    ADD COLUMN cumulative_defect_quantity INTEGER,
    ADD COLUMN pnl_length NUMERIC(14,4),
    ADD COLUMN pnl_width NUMERIC(14,4),
    ADD COLUMN production_notice_order_quantity INTEGER,
    ADD COLUMN unreceived_quantity INTEGER,
    ADD COLUMN last_warehouse_date DATE;

UPDATE qms_work_order
SET material_input_quantity = COALESCE(material_input_quantity, planned_quantity),
    cumulative_scrap_quantity = COALESCE(cumulative_scrap_quantity, 0),
    cumulative_defect_quantity = COALESCE(cumulative_defect_quantity, 0),
    pnl_length = COALESCE(pnl_length, a_board_length),
    pnl_width = COALESCE(pnl_width, a_board_width),
    production_notice_order_quantity = COALESCE(production_notice_order_quantity, production_notice_quantity, customer_order_quantity),
    unreceived_quantity = COALESCE(unreceived_quantity, GREATEST(COALESCE(planned_quantity, 0) - COALESCE(cumulative_warehouse_quantity, completed_quantity, 0), 0)),
    last_warehouse_date = COALESCE(last_warehouse_date, CASE WHEN warehouse_status IN ('RECEIVED', 'CLOSED') THEN DATE(updated_at) END);


UPDATE qms_work_order
SET material_input_quantity = 500,
    cumulative_scrap_quantity = 8,
    cumulative_defect_quantity = 12,
    pnl_length = 495.0000,
    pnl_width = 410.0000,
    production_notice_order_quantity = 520,
    unreceived_quantity = 120,
    last_warehouse_date = '2026-09-04'
WHERE tenant_code = 'demo-pcb' AND work_order_no = 'WO-260902-001';

UPDATE qms_work_order
SET material_input_quantity = 800,
    cumulative_scrap_quantity = 10,
    cumulative_defect_quantity = 15,
    pnl_length = 490.0000,
    pnl_width = 405.0000,
    production_notice_order_quantity = 820,
    unreceived_quantity = 560,
    last_warehouse_date = NULL
WHERE tenant_code = 'demo-pcb' AND work_order_no = 'WO-260902-002';
