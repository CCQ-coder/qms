ALTER TABLE qms_source_document
    ADD COLUMN content_hash CHAR(64) NULL,
    ADD COLUMN processing_updated_at TIMESTAMP NULL,
    ADD CONSTRAINT uq_source_document_import_hash UNIQUE (tenant_code, document_type, content_hash);

ALTER TABLE qms_nonconformity
    MODIFY COLUMN work_order_id VARCHAR(36) NULL;

ALTER TABLE qms_nonconformity_detail
    MODIFY COLUMN work_order_id VARCHAR(36) NULL,
    ADD COLUMN internal_product_no VARCHAR(160) NULL,
    ADD COLUMN layer_count INTEGER NULL;

ALTER TABLE qms_equipment_telemetry
    ADD COLUMN manual_binding_disabled BOOLEAN NOT NULL DEFAULT FALSE;
