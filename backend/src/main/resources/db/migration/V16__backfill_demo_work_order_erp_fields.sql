UPDATE qms_work_order
SET
    customer_code = CASE work_order_no
        WHEN 'WO-260902-001' THEN 'C536'
        WHEN 'WO-260902-002' THEN 'C218'
        ELSE customer_code
    END,
    internal_product_no = COALESCE(internal_product_no, product_code),
    customer_part_no = CASE work_order_no
        WHEN 'WO-260902-001' THEN 'TS-QBF23D-4'
        WHEN 'WO-260902-002' THEN 'RF-4L-024'
        ELSE customer_part_no
    END,
    process_technology = CASE work_order_no
        WHEN 'WO-260902-001' THEN '无铅喷锡'
        WHEN 'WO-260902-002' THEN '沉金'
        ELSE process_technology
    END,
    material = CASE work_order_no
        WHEN 'WO-260902-001' THEN 'FR-4'
        WHEN 'WO-260902-002' THEN 'FR-4'
        ELSE material
    END,
    board_thickness = CASE work_order_no
        WHEN 'WO-260902-001' THEN '1.6 mm'
        WHEN 'WO-260902-002' THEN '1.2 mm'
        ELSE board_thickness
    END,
    copper_thickness = CASE work_order_no
        WHEN 'WO-260902-001' THEN 'H/H'
        WHEN 'WO-260902-002' THEN '1/1 OZ'
        ELSE copper_thickness
    END,
    warehouse_status = COALESCE(warehouse_status, 'PENDING'),
    released_at = COALESCE(released_at, CURRENT_DATE);
