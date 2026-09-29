ALTER TABLE factory_quality_record ADD COLUMN photo_paths JSON NULL AFTER reason;

UPDATE factory_item_state fis
JOIN order_item oi ON oi.id=fis.order_item_id
SET fis.current_process=CASE WHEN oi.category_group='SHOES' THEN 'SHOE_WASH' ELSE 'PROCESSING' END,
    fis.status=CASE WHEN oi.category_group='SHOES' THEN 'WAIT_SHOE_WASH' ELSE 'PROCESSING' END,
    fis.update_time=NOW()
WHERE fis.current_process IN ('SORT','WASH','DRY','IRON');
