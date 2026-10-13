INSERT INTO qms_tenant (id, code, name, status) VALUES
  ('6fa2ad2b-d07d-4f4d-a322-4e45f9a1ac01', 'demo-pcb', '演示 PCB 工厂', 'ACTIVE'),
  ('6fa2ad2b-d07d-4f4d-a322-4e45f9a1ac02', 'customer-a', '客户 A', 'ACTIVE'),
  ('6fa2ad2b-d07d-4f4d-a322-4e45f9a1ac03', 'customer-b', '客户 B', 'ACTIVE');

INSERT INTO qms_system_parameter (id, tenant_code, parameter_key, parameter_value, updated_by, updated_at) VALUES
  ('e68d2983-517f-43d3-bab9-080d93c3be01', 'demo-pcb', 'quality.auto-create-ncr', 'false', 'system', CURRENT_TIMESTAMP),
  ('e68d2983-517f-43d3-bab9-080d93c3be02', 'customer-a', 'quality.auto-create-ncr', 'false', 'system', CURRENT_TIMESTAMP),
  ('e68d2983-517f-43d3-bab9-080d93c3be03', 'customer-b', 'quality.auto-create-ncr', 'false', 'system', CURRENT_TIMESTAMP);

INSERT INTO qms_work_order (
    id, tenant_code, work_order_no, product_code, product_name, specification,
    current_process_code, current_process_name, planned_quantity, completed_quantity,
    source_system, source_key, source_version, status, created_at, updated_at
) VALUES
  ('b454f2e8-a12b-4cfd-9956-000000000001', 'demo-pcb', 'WO-260902-001', 'PCB-4L-001', '四层阻抗板', 'FR-4 / 1.6mm / 4L', 'FQC', '成品检验', 500, 380, 'DEMO', 'WO-260902-001', '1', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('b454f2e8-a12b-4cfd-9956-000000000002', 'demo-pcb', 'WO-260902-002', 'PCB-6L-018', '六层高密度板', 'FR-4 / 1.2mm / 6L', 'SOLDER_MASK', '阻焊', 800, 240, 'DEMO', 'WO-260902-002', '1', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qms_production_batch (id, tenant_code, work_order_id, batch_no, planned_quantity, status, created_at, updated_at) VALUES
  ('c984e21d-4e28-438c-9db9-000000000001', 'demo-pcb', 'b454f2e8-a12b-4cfd-9956-000000000001', 'BATCH-260902-A01', 500, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('c984e21d-4e28-438c-9db9-000000000002', 'demo-pcb', 'b454f2e8-a12b-4cfd-9956-000000000002', 'BATCH-260902-B01', 800, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO qms_inspection_task (
    id, tenant_code, task_no, work_order_id, batch_id, process_code, process_name,
    task_type, source_type, status, assignee_name, due_at, created_by, created_at, updated_at
) VALUES
  ('d15f8383-a2dd-48a8-ae40-000000000001', 'demo-pcb', 'IT-260902-001', 'b454f2e8-a12b-4cfd-9956-000000000001', 'c984e21d-4e28-438c-9db9-000000000001', 'FQC', '成品检验', 'FINISHED', 'AUTO', 'PENDING', '陈检验', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('d15f8383-a2dd-48a8-ae40-000000000002', 'demo-pcb', 'IT-260902-002', 'b454f2e8-a12b-4cfd-9956-000000000002', 'c984e21d-4e28-438c-9db9-000000000002', 'SOLDER_MASK', '阻焊巡检', 'PROCESS', 'AUTO', 'PENDING', '李检验', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

