-- Acknowledgements are immutable history. The latest row for each type is the current review.
CREATE TABLE mrp_workbench_review (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id BIGINT NOT NULL,
  review_type VARCHAR(16) NOT NULL COMMENT 'SCHEDULE/MATERIAL',
  run_id BIGINT NOT NULL,
  schedule_fingerprint CHAR(64) NOT NULL,
  material_fingerprint CHAR(64) DEFAULT NULL,
  operator_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_mwr_batch_type_id (batch_id, review_type, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mrp_material_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  factory_code VARCHAR(32) NOT NULL,
  root_material_code VARCHAR(64) NOT NULL,
  terminal_material_code VARCHAR(64) NOT NULL,
  source_month DATE NOT NULL,
  affected_quantity DECIMAL(18,6) NOT NULL,
  suggested_date DATE DEFAULT NULL,
  reason VARCHAR(512) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  response VARCHAR(512) DEFAULT NULL,
  created_by BIGINT NOT NULL,
  responded_by BIGINT DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  responded_at DATETIME(3) DEFAULT NULL,
  KEY idx_mmf_batch_status (batch_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
