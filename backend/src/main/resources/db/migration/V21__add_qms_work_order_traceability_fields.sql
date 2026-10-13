ALTER TABLE qms_work_order
    ADD COLUMN priority VARCHAR(24),
    ADD COLUMN process_route_version VARCHAR(80),
    ADD COLUMN mi_version VARCHAR(80),
    ADD COLUMN quality_status VARCHAR(24);

UPDATE qms_work_order
SET priority = COALESCE(priority, 'NORMAL'),
    quality_status = COALESCE(quality_status, 'UNASSESSED');

ALTER TABLE qms_work_order
    MODIFY COLUMN priority VARCHAR(24) NOT NULL DEFAULT 'NORMAL',
    MODIFY COLUMN quality_status VARCHAR(24) NOT NULL DEFAULT 'UNASSESSED';

CREATE INDEX idx_work_order_tenant_query_filters
    ON qms_work_order (tenant_code, status, quality_status, warehouse_status, input_date DESC);
