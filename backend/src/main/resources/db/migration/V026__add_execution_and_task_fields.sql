-- V026: Add indexes and columns for async task worker and execution tracking

-- Add index on calc_task for pending task polling
SET @db = DATABASE();

-- Check if index exists on calc_task for status polling
SET @has_idx = (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'calc_task' AND index_name = 'idx_calc_task_status_priority'
);
SET @sql = IF(@has_idx = 0,
  "ALTER TABLE calc_task ADD INDEX idx_calc_task_status_priority (status, priority, created_at)",
  "SELECT 'idx_calc_task_status_priority already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add terminal_material_code column to mrp_daily_execution for easier querying
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'mrp_daily_execution' AND column_name = 'terminal_material_code'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE mrp_daily_execution ADD COLUMN terminal_material_code VARCHAR(64) DEFAULT NULL COMMENT 'Terminal material code for direct queries' AFTER row_key",
  "SELECT 'terminal_material_code already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Ensure mrp_daily_plan_detail has batch_id index for copy operations
SET @has_idx = (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'mrp_daily_plan_detail' AND index_name = 'idx_mdpd_batch_id'
);
SET @sql = IF(@has_idx = 0,
  "ALTER TABLE mrp_daily_plan_detail ADD INDEX idx_mdpd_batch_id (batch_id)",
  "SELECT 'idx_mdpd_batch_id already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
