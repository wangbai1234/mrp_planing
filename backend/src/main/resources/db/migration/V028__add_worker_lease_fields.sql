-- V028: Add worker lease fields to calc_task for proper lease/heartbeat/retry

SET @db = DATABASE();

-- Add worker_id column to calc_task
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'calc_task' AND column_name = 'worker_id'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE calc_task ADD COLUMN worker_id VARCHAR(64) DEFAULT NULL COMMENT 'Worker instance ID that claimed this task' AFTER status",
  "SELECT 'worker_id already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add lease_until column to calc_task
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'calc_task' AND column_name = 'lease_until'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE calc_task ADD COLUMN lease_until TIMESTAMP NULL COMMENT 'Lease expiration time' AFTER worker_id",
  "SELECT 'lease_until already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add heartbeat_at column to calc_task
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'calc_task' AND column_name = 'heartbeat_at'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE calc_task ADD COLUMN heartbeat_at TIMESTAMP NULL COMMENT 'Last heartbeat time' AFTER lease_until",
  "SELECT 'heartbeat_at already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add started_at column to calc_task
SET @has_col = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = @db AND table_name = 'calc_task' AND column_name = 'started_at'
);
SET @sql = IF(@has_col = 0,
  "ALTER TABLE calc_task ADD COLUMN started_at TIMESTAMP NULL COMMENT 'When task execution started' AFTER heartbeat_at",
  "SELECT 'started_at already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add index for lease-based task claiming
SET @has_idx = (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = @db AND table_name = 'calc_task' AND index_name = 'idx_calc_task_lease'
);
SET @sql = IF(@has_idx = 0,
  "ALTER TABLE calc_task ADD INDEX idx_calc_task_lease (status, lease_until, worker_id)",
  "SELECT 'idx_calc_task_lease already exists' AS msg"
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
