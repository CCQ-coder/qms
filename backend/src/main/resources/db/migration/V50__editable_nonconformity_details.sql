ALTER TABLE qms_nonconformity_detail
    ADD COLUMN manually_adjusted TINYINT(1) NOT NULL DEFAULT 0;

UPDATE qms_nonconformity
SET responsible_department = '测试'
WHERE source_type = 'DEVICE' AND process_code = 'FQC'
  AND (responsible_department IS NULL OR TRIM(responsible_department) = '');
