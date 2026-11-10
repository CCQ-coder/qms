ALTER TABLE qms_nonconformity
    ADD COLUMN source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_group_key VARCHAR(160) NULL,
    ADD COLUMN reviewed_by VARCHAR(120) NULL,
    ADD COLUMN reviewed_at TIMESTAMP NULL,
    ADD CONSTRAINT uq_ncr_device_pending UNIQUE (tenant_code, source_group_key);

CREATE INDEX idx_telemetry_zhongbo_material_time
    ON qms_equipment_telemetry (tenant_code, event_type, equipment_id, material_no, device_time);

UPDATE qms_administration_item SET status = 'INACTIVE'
WHERE catalog_type = 'MENU' AND item_code = 'PROCESS_INSPECTION';
