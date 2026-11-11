CREATE TABLE qms_finished_test_settlement (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    material_key VARCHAR(160) NOT NULL,
    work_order_no VARCHAR(80) NOT NULL,
    internal_product_no VARCHAR(160),
    first_detection_at TIMESTAMP NOT NULL,
    last_detection_at TIMESTAMP NOT NULL,
    settled_at TIMESTAMP NOT NULL,
    equipment_count INTEGER NOT NULL,
    tested_count BIGINT NOT NULL,
    good_count BIGINT NOT NULL,
    bad_count BIGINT NOT NULL,
    UNIQUE KEY uq_finished_test_settlement_round (tenant_code, material_key, first_detection_at),
    INDEX idx_finished_test_settlement_latest (tenant_code, last_detection_at)
);
