CREATE INDEX idx_equipment_telemetry_tenant_event_received_id
    ON qms_equipment_telemetry (tenant_code, event_type, received_at DESC, id DESC);
