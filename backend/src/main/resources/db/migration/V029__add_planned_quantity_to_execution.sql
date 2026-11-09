-- V029: Add planned_quantity to mrp_daily_execution for quantity difference tracking

SET @db = DATABASE();

-- Add planned_quantity column to mrp_daily_execution
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'mrp_daily_execution' AND column_name = 'planned_quantity'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE mrp_daily_execution ADD COLUMN planned_quantity DECIMAL(18,6) DEFAULT NULL COMMENT 'Planned quantity at lock time' AFTER actual_quantity",
  "SELECT 'planned_quantity already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
