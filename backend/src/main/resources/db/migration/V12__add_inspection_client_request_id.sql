ALTER TABLE qms_inspection_record ADD COLUMN client_request_id VARCHAR(80);

CREATE UNIQUE INDEX uq_inspection_record_tenant_client_request
    ON qms_inspection_record (tenant_code, client_request_id);
