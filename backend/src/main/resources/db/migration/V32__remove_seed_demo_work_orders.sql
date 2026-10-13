-- Remove the two fixed V2 demonstration production instructions and every dependent record.
-- The source-system condition makes this migration safe for real work orders that reuse a similar number.

DELETE capa
FROM qms_capa capa
JOIN qms_nonconformity ncr ON ncr.id = capa.nonconformity_id
JOIN qms_work_order work_order ON work_order.id = ncr.work_order_id
WHERE work_order.source_system = 'DEMO'
  AND work_order.work_order_no IN ('WO-260902-001', 'WO-260902-002');

DELETE source_document
FROM qms_source_document source_document
JOIN qms_work_order work_order ON work_order.id = source_document.work_order_id
WHERE work_order.source_system = 'DEMO'
  AND work_order.work_order_no IN ('WO-260902-001', 'WO-260902-002');

DELETE ncr
FROM qms_nonconformity ncr
JOIN qms_work_order work_order ON work_order.id = ncr.work_order_id
WHERE work_order.source_system = 'DEMO'
  AND work_order.work_order_no IN ('WO-260902-001', 'WO-260902-002');

DELETE inspection_record
FROM qms_inspection_record inspection_record
JOIN qms_inspection_task inspection_task ON inspection_task.id = inspection_record.task_id
JOIN qms_work_order work_order ON work_order.id = inspection_task.work_order_id
WHERE work_order.source_system = 'DEMO'
  AND work_order.work_order_no IN ('WO-260902-001', 'WO-260902-002');

DELETE inspection_task
FROM qms_inspection_task inspection_task
JOIN qms_work_order work_order ON work_order.id = inspection_task.work_order_id
WHERE work_order.source_system = 'DEMO'
  AND work_order.work_order_no IN ('WO-260902-001', 'WO-260902-002');

DELETE production_batch
FROM qms_production_batch production_batch
JOIN qms_work_order work_order ON work_order.id = production_batch.work_order_id
WHERE work_order.source_system = 'DEMO'
  AND work_order.work_order_no IN ('WO-260902-001', 'WO-260902-002');

DELETE FROM qms_work_order
WHERE source_system = 'DEMO'
  AND work_order_no IN ('WO-260902-001', 'WO-260902-002');
