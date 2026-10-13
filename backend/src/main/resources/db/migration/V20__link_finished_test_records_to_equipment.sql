-- Preserve the workstation selected when an FQC record is submitted so the
-- device record history remains auditable even after the task is completed.
ALTER TABLE qms_inspection_record
    ADD COLUMN equipment_asset_no VARCHAR(80);

CREATE INDEX idx_inspection_record_tenant_equipment_time
    ON qms_inspection_record (tenant_code, equipment_asset_no, inspected_at DESC);
