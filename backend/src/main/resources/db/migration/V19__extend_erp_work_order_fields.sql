ALTER TABLE qms_work_order
    ADD COLUMN material_input_date DATE,
    ADD COLUMN pcs_length NUMERIC(14,4),
    ADD COLUMN pcs_width NUMERIC(14,4),
    ADD COLUMN material_no VARCHAR(80),
    ADD COLUMN material_name VARCHAR(160),
    ADD COLUMN material_specification VARCHAR(300),
    ADD COLUMN standard_material_consumption NUMERIC(14,4),
    ADD COLUMN cumulative_warehouse_quantity INTEGER,
    ADD COLUMN required_delivery_date DATE,
    ADD COLUMN production_notice_no VARCHAR(80),
    ADD COLUMN input_date DATE,
    ADD COLUMN single_pcs_area NUMERIC(14,6),
    ADD COLUMN a_board_length NUMERIC(14,4),
    ADD COLUMN a_board_width NUMERIC(14,4),
    ADD COLUMN b_board_length NUMERIC(14,4),
    ADD COLUMN b_board_width NUMERIC(14,4),
    ADD COLUMN forming_method VARCHAR(80),
    ADD COLUMN layer_count INTEGER,
    ADD COLUMN production_notice_quantity INTEGER,
    ADD COLUMN customer_order VARCHAR(120),
    ADD COLUMN customer_order_no VARCHAR(120),
    ADD COLUMN customer_order_quantity INTEGER,
    ADD COLUMN material_area NUMERIC(14,4),
    ADD COLUMN production_notice_area NUMERIC(14,4),
    ADD COLUMN customer_order_area NUMERIC(14,4);

UPDATE qms_work_order
SET material_input_date = COALESCE(material_input_date, released_at),
    input_date = COALESCE(input_date, released_at),
    cumulative_warehouse_quantity = COALESCE(cumulative_warehouse_quantity, completed_quantity);

CREATE INDEX idx_work_order_tenant_input_date ON qms_work_order (tenant_code, input_date DESC);
