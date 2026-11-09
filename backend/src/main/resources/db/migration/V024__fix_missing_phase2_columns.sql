-- V024: Add inventory_quality to mrp_inventory_balance and revision fields to mrp_calc_batch
-- These columns were added manually to the dev database but missing from test database
-- Using ALTER TABLE with error suppression for idempotency

-- inventory_quality column
SET @db = DATABASE();
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'mrp_inventory_balance' AND column_name = 'inventory_quality'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE mrp_inventory_balance ADD COLUMN inventory_quality VARCHAR(16) NOT NULL DEFAULT 'GOOD' COMMENT 'GOOD/DEFECTIVE' AFTER material_code",
  "SELECT 'inventory_quality already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- revision_of_batch_id column
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'mrp_calc_batch' AND column_name = 'revision_of_batch_id'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE mrp_calc_batch ADD COLUMN revision_of_batch_id BIGINT NULL COMMENT 'Original batch this revises' AFTER active_request_key",
  "SELECT 'revision_of_batch_id already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- revision_no column
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'mrp_calc_batch' AND column_name = 'revision_no'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE mrp_calc_batch ADD COLUMN revision_no INT NOT NULL DEFAULT 0 COMMENT 'Revision sequence number' AFTER revision_of_batch_id",
  "SELECT 'revision_no already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- superseded_by_batch_id column
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'mrp_calc_batch' AND column_name = 'superseded_by_batch_id'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE mrp_calc_batch ADD COLUMN superseded_by_batch_id BIGINT NULL COMMENT 'Newer revision that supersedes this' AFTER revision_no",
  "SELECT 'superseded_by_batch_id already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- mrp_daily_execution table
CREATE TABLE IF NOT EXISTS mrp_daily_execution (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  row_key VARCHAR(512) NOT NULL COMMENT 'Stable row identifier matching daily_plan_detail',
  schedule_date DATE NOT NULL,
  execution_status VARCHAR(16) NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/LOCKED/EXECUTED',
  actual_quantity DECIMAL(18,6) DEFAULT NULL COMMENT 'Actual completed quantity',
  confirmed_by BIGINT DEFAULT NULL COMMENT 'User ID who confirmed',
  confirmed_at DATETIME(3) DEFAULT NULL,
  remark VARCHAR(512) DEFAULT NULL,
  revision INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mde_batch_row_date (batch_id, row_key, schedule_date),
  KEY idx_mde_batch_date (batch_id, schedule_date),
  KEY idx_mde_batch_status (batch_id, execution_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
