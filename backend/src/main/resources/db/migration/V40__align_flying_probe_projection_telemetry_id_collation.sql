-- The projection lookup compares these two columns directly; installations can have
-- an older telemetry table collation than the database default used by V39.
ALTER TABLE qms_flying_probe_projection
    MODIFY COLUMN telemetry_id VARCHAR(36)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;
