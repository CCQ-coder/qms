ALTER TABLE qms_user_access_profile
    ADD COLUMN password_hash VARCHAR(100) NULL AFTER status,
    ADD COLUMN password_changed_at TIMESTAMP NULL AFTER password_hash;
