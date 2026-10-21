ALTER TABLE qms_nonconformity
    MODIFY COLUMN inspection_record_id VARCHAR(36) NULL,
    ADD COLUMN detection_date DATE NULL,
    ADD COLUMN shift_name VARCHAR(80) NULL,
    ADD COLUMN process_code VARCHAR(80) NULL,
    ADD COLUMN process_name VARCHAR(160) NULL,
    ADD COLUMN responsible_department VARCHAR(160) NULL,
    ADD COLUMN remark TEXT NULL,
    ADD COLUMN disposition_method VARCHAR(32) NOT NULL DEFAULT 'SCRAP',
    ADD COLUMN batch_no VARCHAR(120) NULL;

CREATE TABLE qms_nonconformity_detail (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    nonconformity_id VARCHAR(36) NOT NULL REFERENCES qms_nonconformity(id),
    line_no INTEGER NOT NULL,
    work_order_id VARCHAR(36) NOT NULL REFERENCES qms_work_order(id),
    process_code VARCHAR(80) NOT NULL,
    process_name VARCHAR(160) NOT NULL,
    unit VARCHAR(32) NOT NULL DEFAULT 'PCS',
    single_pcs_area DECIMAL(18, 6) NULL,
    scrap_quantity INTEGER NOT NULL,
    scrap_area DECIMAL(18, 6) NULL,
    scrap_reason VARCHAR(500) NULL,
    remark TEXT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    CONSTRAINT uq_ncr_detail_line UNIQUE (nonconformity_id, line_no)
);
CREATE INDEX idx_ncr_detail_tenant_ncr ON qms_nonconformity_detail (tenant_code, nonconformity_id);

UPDATE qms_nonconformity ncr
JOIN qms_inspection_record ir ON ir.id = ncr.inspection_record_id
JOIN qms_inspection_task task ON task.id = ir.task_id
LEFT JOIN qms_production_batch batch ON batch.id = ncr.batch_id
SET ncr.detection_date = DATE(ir.inspected_at),
    ncr.process_code = task.process_code,
    ncr.process_name = task.process_name,
    ncr.remark = ir.defect_summary,
    ncr.batch_no = batch.batch_no;

INSERT INTO qms_nonconformity_detail (
    id, tenant_code, nonconformity_id, line_no, work_order_id, process_code, process_name,
    unit, single_pcs_area, scrap_quantity, scrap_area, scrap_reason, remark, status
)
SELECT UUID(), ncr.tenant_code, ncr.id, 1, work_order.id, task.process_code, task.process_name,
       'PCS', work_order.single_pcs_area, ir.defective_quantity,
       work_order.single_pcs_area * ir.defective_quantity, ir.defect_summary,
       ir.defect_location, ncr.status
FROM qms_nonconformity ncr
JOIN qms_inspection_record ir ON ir.id = ncr.inspection_record_id
JOIN qms_inspection_task task ON task.id = ir.task_id
JOIN qms_work_order work_order ON work_order.id = ncr.work_order_id
WHERE ir.defective_quantity > 0;

UPDATE qms_administration_item
SET item_name = '报表'
WHERE catalog_type = 'MENU' AND item_code = 'TRACE' AND item_name = '追溯与分析';

UPDATE qms_administration_item
SET parent_code = 'QUALITY', sort_order = 26
WHERE catalog_type = 'MENU' AND item_code = 'NONCONFORMITIES' AND parent_code = 'TRACE';
