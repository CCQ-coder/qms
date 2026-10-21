UPDATE qms_administration_item
SET parent_code = 'TRACE', sort_order = 34
WHERE catalog_type = 'MENU'
  AND item_code = 'EQUIPMENT_FINISHED_TEST_SUMMARY';
