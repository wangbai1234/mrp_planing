-- Soft-delete flags for the four versioned configs that follow the business-week rule.
-- Existing foreign keys stay. Application code marks unreferenced drafts deleted
-- instead of removing the row, so a published or batch-locked version cannot disappear.

ALTER TABLE factory_warehouse_mapping_version
    ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER published_at,
    ADD COLUMN deleted_at DATETIME(3) NULL AFTER is_deleted;

ALTER TABLE stock_policy_version
    ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER published_at,
    ADD COLUMN deleted_at DATETIME(3) NULL AFTER is_deleted;

ALTER TABLE mrp_material_policy_version
    ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER published_at,
    ADD COLUMN deleted_at DATETIME(3) NULL AFTER is_deleted;

ALTER TABLE production_calendar_version
    ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER published_at,
    ADD COLUMN deleted_at DATETIME(3) NULL AFTER is_deleted;
