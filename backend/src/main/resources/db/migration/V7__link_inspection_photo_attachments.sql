ALTER TABLE qms_source_document
    ADD COLUMN inspection_record_id VARCHAR(36) REFERENCES qms_inspection_record(id);

CREATE INDEX idx_source_document_tenant_inspection_record
    ON qms_source_document (tenant_code, inspection_record_id);
