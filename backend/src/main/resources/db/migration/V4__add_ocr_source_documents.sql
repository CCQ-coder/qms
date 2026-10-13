CREATE TABLE qms_source_document (
    id VARCHAR(36) PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    extracted_payload_json TEXT,
    confirmed_payload_json TEXT,
    work_order_id VARCHAR(36) REFERENCES qms_work_order(id),
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP
);
CREATE INDEX idx_source_document_tenant_status ON qms_source_document (tenant_code, status, created_at DESC);

