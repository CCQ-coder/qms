ALTER TABLE qms_equipment
    ADD COLUMN manufacturer VARCHAR(160) NULL AFTER equipment_type,
    ADD COLUMN model_name VARCHAR(160) NULL AFTER manufacturer;
