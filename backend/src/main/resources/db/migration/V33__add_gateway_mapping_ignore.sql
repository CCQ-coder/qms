ALTER TABLE qms_equipment_gateway_mapping
    MODIFY equipment_id VARCHAR(36) NULL,
    ADD COLUMN ignored BOOLEAN NOT NULL DEFAULT FALSE;
