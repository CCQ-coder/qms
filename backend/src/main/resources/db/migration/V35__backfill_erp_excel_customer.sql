-- The production-instruction export calls this field “客户”, not “客户编号”.
-- Older direct imports kept the raw value in erp_customer but left customer_code empty.
-- The raw column is created by the ERP sync tool, so it may not exist in a fresh QMS database.
SET @qms_customer_backfill_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'qms_work_order'
          AND column_name = 'erp_customer'
    ),
    'UPDATE qms_work_order SET customer_code = TRIM(erp_customer) WHERE source_system = ''ERP_EXCEL'' AND (customer_code IS NULL OR TRIM(customer_code) = '''') AND erp_customer IS NOT NULL AND CHAR_LENGTH(TRIM(erp_customer)) BETWEEN 1 AND 80',
    'SELECT 1'
);
PREPARE qms_customer_backfill FROM @qms_customer_backfill_sql;
EXECUTE qms_customer_backfill;
DEALLOCATE PREPARE qms_customer_backfill;
