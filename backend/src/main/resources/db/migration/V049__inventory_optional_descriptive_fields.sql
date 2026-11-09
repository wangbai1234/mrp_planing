-- Descriptive inventory fields do not participate in stock aggregation or MRP.
ALTER TABLE inventory_item
    MODIFY COLUMN inventory_category VARCHAR(64) NULL,
    MODIFY COLUMN supplier_name VARCHAR(255) NULL;
