-- Flying-probe snapshots match the tenant's normalized internal product number.
-- Keep the expression indexed so a backlog does not scan every work order per snapshot.
ALTER TABLE qms_work_order
    ADD COLUMN internal_product_no_match_key VARCHAR(80)
        GENERATED ALWAYS AS (UPPER(TRIM(internal_product_no))) STORED;

CREATE INDEX idx_work_order_tenant_internal_match
    ON qms_work_order (tenant_code, internal_product_no_match_key);
