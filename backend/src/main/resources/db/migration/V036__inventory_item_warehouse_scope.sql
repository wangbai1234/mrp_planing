-- Inventory files may contain multiple warehouses. Warehouse scope belongs to each detail row.
ALTER TABLE inventory_item
    ADD COLUMN warehouse_code VARCHAR(32) NULL COMMENT 'Warehouse code from the imported row' AFTER inventory_quality,
    ADD COLUMN warehouse_name VARCHAR(128) NULL COMMENT 'Warehouse name from the imported row' AFTER warehouse_code,
    ADD INDEX idx_inventory_item_snapshot_warehouse (snapshot_id, warehouse_code, material_id);

UPDATE inventory_item i
JOIN inventory_snapshot s ON s.id = i.snapshot_id
SET i.warehouse_code = s.warehouse_code
WHERE i.warehouse_code IS NULL AND s.warehouse_code IS NOT NULL;
