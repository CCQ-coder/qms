ALTER TABLE qms_process
    ADD COLUMN responsible_department VARCHAR(160) NULL;

UPDATE qms_process
SET responsible_department = '测试'
WHERE process_code = 'FQC';
