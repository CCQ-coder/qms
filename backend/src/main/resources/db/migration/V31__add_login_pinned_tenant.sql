ALTER TABLE qms_tenant
    ADD COLUMN login_pinned TINYINT(1) NOT NULL DEFAULT 0 AFTER status;

ALTER TABLE qms_tenant
    ADD COLUMN login_pinned_unique TINYINT
        GENERATED ALWAYS AS (CASE WHEN login_pinned = 1 THEN 1 ELSE NULL END) STORED;

CREATE UNIQUE INDEX uq_qms_tenant_single_login_pinned
    ON qms_tenant (login_pinned_unique);
