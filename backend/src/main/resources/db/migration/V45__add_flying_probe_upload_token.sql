ALTER TABLE qms_equipment_gateway_mapping
    ADD COLUMN upload_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE KEY uk_gateway_mapping_upload_token_hash (upload_token_hash);

ALTER TABLE qms_gateway_host_status
    MODIFY COLUMN sampled_at TIMESTAMP(6) NOT NULL;
