-- V023: Add inventory_quality column, change BIGINT quantities to DECIMAL(18,6), add BOM snapshot error_message

-- Add inventory_quality column to inventory_item
ALTER TABLE inventory_item ADD COLUMN inventory_quality VARCHAR(16) NOT NULL DEFAULT 'GOOD' COMMENT 'GOOD/DEFECTIVE' AFTER material_id;

-- Change BIGINT quantity columns to DECIMAL(18,6)
ALTER TABLE inventory_item MODIFY COLUMN odm_supplier_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000;
ALTER TABLE inventory_item MODIFY COLUMN xa400_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000;
ALTER TABLE inventory_item MODIFY COLUMN xa378_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000;
ALTER TABLE inventory_item MODIFY COLUMN xa226_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000;
ALTER TABLE inventory_item MODIFY COLUMN shipping_available_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000;
ALTER TABLE inventory_item MODIFY COLUMN order_pending_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000;

-- Add error_message to bom_snapshot for validation error reporting
ALTER TABLE bom_snapshot ADD COLUMN error_message TEXT COMMENT 'Error details when status=FAILED' AFTER checksum;
