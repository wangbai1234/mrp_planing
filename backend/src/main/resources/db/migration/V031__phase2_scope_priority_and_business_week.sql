-- V031: Persist the confirmed Phase 2 priority, warehouse and business-week rules.

ALTER TABLE forecast_detail
    ADD COLUMN source_row_no INT NOT NULL DEFAULT 0 COMMENT 'Original confirmed import row order' AFTER version_id,
    ADD COLUMN priority_seq INT NOT NULL DEFAULT 0 COMMENT 'Frozen allocation priority within forecast version' AFTER source_row_no,
    ADD INDEX idx_fd_version_priority (version_id, priority_seq, id);

UPDATE forecast_detail fd
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY version_id ORDER BY id) AS seq
    FROM forecast_detail
) ranked ON ranked.id = fd.id
SET fd.source_row_no = ranked.seq,
    fd.priority_seq = ranked.seq
WHERE fd.priority_seq = 0;

ALTER TABLE inventory_snapshot
    ADD COLUMN warehouse_code VARCHAR(32) NULL COMMENT 'Warehouse selected before importing this file' AFTER snapshot_date,
    ADD INDEX idx_inventory_snapshot_warehouse (warehouse_code, snapshot_date, id);

UPDATE mrp_inventory_balance SET warehouse_scope = 'UNASSIGNED' WHERE warehouse_scope IS NULL OR warehouse_scope = '';

-- Historical environments used two names for the same legacy unique key. Resolve the
-- actual key name instead of assuming a single name, otherwise an existing V030 schema
-- cannot be upgraded even though an empty-schema migration succeeds.
SET @legacy_mib_unique_key = (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'mrp_inventory_balance'
      AND index_name IN (
          'uk_mib_snapshot_factory_org_material_warehouse',
          'uk_mib_snapshot_factory_org_material_quality',
          'uk_snapshot_factory_material'
      )
    ORDER BY FIELD(index_name,
        'uk_mib_snapshot_factory_org_material_warehouse',
        'uk_mib_snapshot_factory_org_material_quality',
        'uk_snapshot_factory_material')
    LIMIT 1
);
SET @drop_legacy_mib_unique_sql = IF(
    @legacy_mib_unique_key IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE mrp_inventory_balance DROP INDEX `', @legacy_mib_unique_key, '`')
);
PREPARE drop_legacy_mib_unique_stmt FROM @drop_legacy_mib_unique_sql;
EXECUTE drop_legacy_mib_unique_stmt;
DEALLOCATE PREPARE drop_legacy_mib_unique_stmt;

ALTER TABLE mrp_inventory_balance
    MODIFY COLUMN warehouse_scope VARCHAR(128) NOT NULL COMMENT 'Warehouse code selected on the snapshot header',
    ADD UNIQUE KEY uk_mib_snapshot_warehouse_material_quality
        (inventory_snapshot_id, warehouse_scope, material_code, inventory_quality);

CREATE TABLE factory_warehouse_mapping_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_no INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_date DATE NULL,
    checksum VARCHAR(64) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fwmv_version_no (version_no),
    KEY idx_fwmv_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE factory_warehouse_mapping_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    factory_code VARCHAR(32) NOT NULL,
    factory_name VARCHAR(128) NULL,
    warehouse_code VARCHAR(32) NOT NULL,
    warehouse_name VARCHAR(128) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fwmd_version_factory (version_id, factory_code),
    UNIQUE KEY uk_fwmd_version_warehouse (version_id, warehouse_code),
    CONSTRAINT fk_fwmd_version FOREIGN KEY (version_id)
        REFERENCES factory_warehouse_mapping_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stock_policy_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_no INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_date DATE NULL,
    checksum VARCHAR(64) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_spv_version_no (version_no),
    KEY idx_spv_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stock_policy_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    warehouse_code VARCHAR(32) NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    safety_stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
    warning_stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
    PRIMARY KEY (id),
    UNIQUE KEY uk_spd_version_warehouse_material (version_id, warehouse_code, material_code),
    CONSTRAINT chk_spd_non_negative CHECK (safety_stock_qty >= 0 AND warning_stock_qty >= 0),
    CONSTRAINT chk_spd_safety_warning CHECK (safety_stock_qty <= warning_stock_qty),
    CONSTRAINT fk_spd_version FOREIGN KEY (version_id)
        REFERENCES stock_policy_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE business_week_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_no INT NOT NULL,
    business_month DATE NOT NULL COMMENT 'First day of the configured business month',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    checksum VARCHAR(64) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bwv_month_version (business_month, version_no),
    KEY idx_bwv_month_status (business_month, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE business_week_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    week_code VARCHAR(16) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    sort_no INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bwd_version_week (version_id, week_code),
    UNIQUE KEY uk_bwd_version_sort (version_id, sort_no),
    CONSTRAINT chk_bwd_date_range CHECK (start_date <= end_date),
    CONSTRAINT fk_bwd_version FOREIGN KEY (version_id)
        REFERENCES business_week_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE mrp_calc_batch
    ADD COLUMN factory_warehouse_mapping_version_id BIGINT NULL AFTER production_calendar_version_id,
    ADD COLUMN stock_policy_version_id BIGINT NULL AFTER factory_warehouse_mapping_version_id,
    ADD COLUMN business_week_version_id BIGINT NULL AFTER stock_policy_version_id,
    ADD COLUMN inventory_snapshot_group_id BIGINT NULL AFTER inventory_snapshot_id;

CREATE TABLE inventory_snapshot_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_name VARCHAR(128) NOT NULL,
    snapshot_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    checksum VARCHAR(64) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_isg_status_date (status, snapshot_date, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inventory_snapshot_group_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    warehouse_code VARCHAR(32) NOT NULL,
    inventory_snapshot_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_isgm_group_warehouse (group_id, warehouse_code),
    UNIQUE KEY uk_isgm_group_snapshot (group_id, inventory_snapshot_id),
    CONSTRAINT fk_isgm_group FOREIGN KEY (group_id)
        REFERENCES inventory_snapshot_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_isgm_snapshot FOREIGN KEY (inventory_snapshot_id)
        REFERENCES inventory_snapshot(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mrp_batch_factory_scope (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    factory_code VARCHAR(32) NOT NULL,
    warehouse_code VARCHAR(32) NOT NULL,
    production_calendar_version_id BIGINT NULL,
    inventory_snapshot_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mbfs_batch_factory (batch_id, factory_code),
    UNIQUE KEY uk_mbfs_batch_warehouse (batch_id, warehouse_code),
    CONSTRAINT fk_mbfs_batch FOREIGN KEY (batch_id)
        REFERENCES mrp_calc_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE mrp_inventory_pool
    ADD COLUMN warehouse_code VARCHAR(32) NULL AFTER factory_code,
    ADD COLUMN warning_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000 AFTER reserved_qty;

ALTER TABLE mrp_inventory_allocation
    ADD COLUMN priority_seq INT NOT NULL DEFAULT 0 AFTER source_month,
    ADD COLUMN allocation_type VARCHAR(32) NOT NULL DEFAULT 'AUTO_PRIORITY' AFTER allocated_qty,
    ADD COLUMN adjustment_reason VARCHAR(512) NULL AFTER allocation_type;
