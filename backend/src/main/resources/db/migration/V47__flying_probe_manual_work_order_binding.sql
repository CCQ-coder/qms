ALTER TABLE qms_flying_probe_run
    ADD COLUMN binding_source VARCHAR(16) NOT NULL DEFAULT 'AUTO' AFTER work_order_no,
    ADD COLUMN bound_by VARCHAR(160) NULL AFTER binding_source,
    ADD COLUMN bound_at TIMESTAMP NULL AFTER bound_by;
