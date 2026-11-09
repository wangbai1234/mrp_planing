-- Factory master and one-to-many warehouse ownership. Historical mapping snapshots remain untouched.
CREATE TABLE mrp_factory (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    factory_code VARCHAR(32) NOT NULL,
    factory_name VARCHAR(128) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_mrp_factory_code (factory_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO mrp_factory(factory_code, factory_name) VALUES ('爱培科', '爱培科'), ('永惠', '永惠');
INSERT IGNORE INTO mrp_factory(factory_code, factory_name)
SELECT DISTINCT factory_code, COALESCE(NULLIF(factory_name, ''), factory_code)
FROM factory_warehouse_mapping WHERE factory_code NOT IN ('爱培科', '永惠');

ALTER TABLE factory_warehouse_mapping
    DROP INDEX uk_fwm_factory,
    ADD COLUMN warehouse_manager VARCHAR(128) NULL AFTER warehouse_name,
    ADD COLUMN supplier_name VARCHAR(128) NULL AFTER warehouse_manager,
    ADD COLUMN is_good_warehouse TINYINT(1) NOT NULL DEFAULT 1 AFTER supplier_name,
    ADD COLUMN warehouse_attribute VARCHAR(32) NULL AFTER is_good_warehouse,
    ADD COLUMN priority_no INT NULL AFTER warehouse_attribute,
    ADD COLUMN quality_configured TINYINT(1) NOT NULL DEFAULT 0 AFTER priority_no,
    ADD KEY idx_fwm_factory (factory_code),
    ADD CONSTRAINT chk_fwm_priority CHECK (priority_no IS NULL OR priority_no > 0);

-- Legacy rows require explicit quality and attribute confirmation before new imports.
ALTER TABLE factory_warehouse_mapping_detail
    DROP INDEX uk_fwmd_version_factory,
    ADD COLUMN warehouse_manager VARCHAR(128) NULL AFTER warehouse_name,
    ADD COLUMN supplier_name VARCHAR(128) NULL AFTER warehouse_manager,
    ADD COLUMN is_good_warehouse TINYINT(1) NULL AFTER supplier_name,
    ADD COLUMN warehouse_attribute VARCHAR(32) NULL AFTER is_good_warehouse,
    ADD COLUMN priority_no INT NULL AFTER warehouse_attribute,
    ADD COLUMN maintenance_order BIGINT NULL AFTER priority_no,
    ADD KEY idx_fwmd_version_factory (version_id, factory_code),
    ADD CONSTRAINT chk_fwmd_priority CHECK (priority_no IS NULL OR priority_no > 0);

ALTER TABLE mrp_batch_factory_scope
    DROP INDEX uk_mbfs_batch_factory,
    ADD UNIQUE KEY uk_mbfs_batch_factory_warehouse (batch_id, factory_code, warehouse_code);
