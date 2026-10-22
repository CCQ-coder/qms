-- Older QMS databases default to general_ci while equipment mappings use 0900_ai_ci.
ALTER TABLE qms_gateway_host_status
    MODIFY COLUMN tenant_code VARCHAR(64)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    MODIFY COLUMN host_name VARCHAR(160)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;
