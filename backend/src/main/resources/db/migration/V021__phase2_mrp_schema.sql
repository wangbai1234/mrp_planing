-- V021: Phase 2 MRP Schema
-- This migration creates all Phase 2 tables for MRP multi-layer net requirements and daily scheduling

CREATE TABLE IF NOT EXISTS bom_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_version VARCHAR(64) DEFAULT NULL COMMENT 'Source BOM sync version identifier',
  status VARCHAR(32) NOT NULL DEFAULT 'CREATING' COMMENT 'CREATING/READY/FAILED',
  checksum VARCHAR(64) DEFAULT NULL,
  effective_at DATE DEFAULT NULL COMMENT 'asOfDate for BOM selection',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_bs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bom_snapshot_detail (
  id BIGINT NOT NULL AUTO_INCREMENT,
  snapshot_id BIGINT NOT NULL,
  parent_code VARCHAR(64) NOT NULL,
  child_code VARCHAR(64) NOT NULL,
  parent_qty DECIMAL(18,6) NOT NULL COMMENT 'Parent base quantity',
  child_qty DECIMAL(18,6) NOT NULL COMMENT 'Child quantity per parent',
  bom_version VARCHAR(64) DEFAULT NULL,
  effective_date DATE DEFAULT NULL,
  organization_code VARCHAR(64) DEFAULT NULL,
  body_scope VARCHAR(64) DEFAULT NULL,
  path_metadata VARCHAR(512) DEFAULT NULL COMMENT 'JSON or text for extra BOM attributes',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_bsd_snapshot_org_body_parent_bom_child (snapshot_id, organization_code, body_scope, parent_code, bom_version, child_code),
  KEY idx_bsd_snapshot_parent (snapshot_id, parent_code),
  KEY idx_bsd_snapshot_child (snapshot_id, child_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_category_role_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version_no INT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
  checksum VARCHAR(64) DEFAULT NULL,
  created_by BIGINT DEFAULT NULL,
  published_at DATETIME(3) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mcrv_version_no (version_no),
  KEY idx_mcrv_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_category_role_detail (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version_id BIGINT NOT NULL,
  category_code VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL COMMENT 'FINISHED_GOOD/HEAD_ASSEMBLY/PCBA/RAW_MATERIAL/OTHER/EXCLUDED/UNMAINTAINED',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  revision INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mcrd_version_category (version_id, category_code),
  KEY idx_mcrd_version (version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_material_policy_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version_no INT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
  checksum VARCHAR(64) DEFAULT NULL,
  created_by BIGINT DEFAULT NULL,
  published_at DATETIME(3) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mmpv_version_no (version_no),
  KEY idx_mmpv_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_material_policy_detail (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  supply_mode VARCHAR(32) NOT NULL DEFAULT 'UNMAINTAINED' COMMENT 'SELF_MADE/PURCHASED/OUTSOURCED/UNMAINTAINED',
  explosion_policy VARCHAR(32) NOT NULL DEFAULT 'UNMAINTAINED' COMMENT 'EXPLODE/STOP/UNMAINTAINED',
  quantity_scale INT DEFAULT NULL COMMENT 'Decimal places for this material, null=use default',
  revision INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mmpd_version_material (version_id, material_code),
  KEY idx_mmpd_version (version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS production_calendar_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version_no INT NOT NULL,
  factory_code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/RETIRED',
  checksum VARCHAR(64) DEFAULT NULL,
  created_by BIGINT DEFAULT NULL,
  published_at DATETIME(3) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_pcv_version_no (version_no),
  KEY idx_pcv_status_factory (status, factory_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS production_calendar_day (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version_id BIGINT NOT NULL,
  factory_code VARCHAR(32) NOT NULL,
  calendar_date DATE NOT NULL,
  is_production_day TINYINT(1) NOT NULL DEFAULT 1,
  allocation_weight DECIMAL(18,6) NOT NULL DEFAULT 1.000000,
  daily_capacity DECIMAL(18,6) DEFAULT NULL COMMENT 'Nullable, for display only',
  reason VARCHAR(256) DEFAULT NULL,
  revision INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_pcd_version_factory_date (version_id, factory_code, calendar_date),
  KEY idx_pcd_version_factory (version_id, factory_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_inventory_balance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  inventory_snapshot_id BIGINT NOT NULL,
  factory_code VARCHAR(32) NOT NULL,
  inventory_org VARCHAR(64) NOT NULL,
  warehouse_scope VARCHAR(128) DEFAULT NULL COMMENT 'Warehouse type/scope filter applied',
  material_code VARCHAR(64) NOT NULL,
  available_quantity DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  source_row_ref VARCHAR(128) DEFAULT NULL COMMENT 'Reference to original import row',
  included_flag TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether included in calculation',
  normalization_rule_version VARCHAR(32) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mib_snapshot_factory_org_material_warehouse (inventory_snapshot_id, factory_code, inventory_org, material_code, warehouse_scope),
  KEY idx_mib_snapshot_factory_material (inventory_snapshot_id, factory_code, material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_calc_batch (
  id BIGINT NOT NULL AUTO_INCREMENT,
  factory_code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CHECKING/ANALYZING/WAITING_DECISION/REANALYZING/READY_TO_CALCULATE/CALCULATING/WRITING/READY/PUBLISHED/STALE/FAILED/CANCELLED',
  forecast_version_id BIGINT DEFAULT NULL,
  inventory_snapshot_id BIGINT DEFAULT NULL,
  bom_snapshot_id BIGINT DEFAULT NULL,
  category_role_version_id BIGINT DEFAULT NULL,
  material_policy_version_id BIGINT DEFAULT NULL,
  production_calendar_version_id BIGINT DEFAULT NULL,
  decision_checksum VARCHAR(64) DEFAULT NULL,
  rule_version VARCHAR(32) NOT NULL DEFAULT 'v2',
  input_checksum VARCHAR(64) DEFAULT NULL,
  result_checksum VARCHAR(64) DEFAULT NULL,
  version_no INT NOT NULL,
  active_run_id BIGINT DEFAULT NULL COMMENT 'Currently active mrp_calc_run',
  published_at DATETIME(3) DEFAULT NULL,
  active_request_key VARCHAR(128) DEFAULT NULL COMMENT 'Idempotency key',
  issue_count INT NOT NULL DEFAULT 0,
  unresolved_count INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
  created_by BIGINT DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_mcb_status_created (status, created_at),
  KEY idx_mcb_factory_input_status (factory_code, input_checksum, status),
  KEY idx_mcb_factory_status (factory_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_calc_run (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/WRITING/VALIDATING/ACTIVE/FAILED/CANCELLED',
  result_checksum VARCHAR(64) DEFAULT NULL,
  row_counts_json TEXT COMMENT 'JSON with row counts per table',
  started_at DATETIME(3) DEFAULT NULL,
  finished_at DATETIME(3) DEFAULT NULL,
  error_code VARCHAR(64) DEFAULT NULL,
  error_message TEXT,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_mcr_batch_status (batch_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_calc_exception (
  id BIGINT NOT NULL AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  severity VARCHAR(16) NOT NULL COMMENT 'ERROR/WARNING/INFO',
  error_code VARCHAR(64) NOT NULL,
  scope_key VARCHAR(256) DEFAULT NULL,
  message TEXT NOT NULL,
  detail_json TEXT,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_mce_run_severity_code_scope (run_id, severity, error_code, scope_key),
  KEY idx_mce_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_decision_issue (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  issue_key VARCHAR(128) NOT NULL COMMENT 'Stable idempotent key',
  issue_type VARCHAR(64) NOT NULL COMMENT 'ROLE_MISSING/SUPPLY_MODE_MISSING/EXPLOSION_POLICY_MISSING/INVENTORY_MISSING/SHARED_INVENTORY/EXPLODE_WITHOUT_BOM/MULTIPLE_ACTIVE_BOM/INVALID_BOM_QUANTITY/BOM_CYCLE/TARGET_NON_PRODUCTION_DAY/NO_PRODUCTION_DAY',
  severity VARCHAR(16) NOT NULL DEFAULT 'ERROR' COMMENT 'ERROR/WARNING/INFO',
  scope_type VARCHAR(32) DEFAULT NULL COMMENT 'MATERIAL/FACTORY/BATCH',
  scope_key VARCHAR(128) DEFAULT NULL,
  payload_json TEXT COMMENT 'Detailed issue context',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/DISMISSED',
  revision INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  resolved_at DATETIME(3) DEFAULT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mdi_batch_issue_key (batch_id, issue_key),
  KEY idx_mdi_batch_status_severity (batch_id, status, severity, issue_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_decision_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  issue_id BIGINT NOT NULL,
  decision_code VARCHAR(64) NOT NULL,
  decision_payload TEXT COMMENT 'JSON with decision details',
  reason VARCHAR(512) DEFAULT NULL,
  operator_id BIGINT DEFAULT NULL,
  expected_revision INT DEFAULT NULL COMMENT 'Optimistic lock check',
  decision_checksum VARCHAR(64) DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_mdr_issue (issue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_inventory_pool (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  snapshot_id BIGINT NOT NULL,
  factory_code VARCHAR(32) NOT NULL,
  inventory_org VARCHAR(64) NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  available_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  reserved_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  allocated_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_mip_batch_factory_material (batch_id, factory_code, material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_inventory_allocation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  pool_id BIGINT NOT NULL,
  demand_key VARCHAR(256) NOT NULL COMMENT 'Stable demand identifier',
  root_material_code VARCHAR(64) NOT NULL,
  source_month DATE NOT NULL COMMENT 'First day of source month',
  path_hash VARCHAR(64) DEFAULT NULL,
  gross_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  allocated_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  manual_confirmed TINYINT(1) NOT NULL DEFAULT 0,
  revision INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mia_pool_demand (pool_id, demand_key),
  KEY idx_mia_pool (pool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_requirement_detail (
  id BIGINT NOT NULL AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  factory_code VARCHAR(32) NOT NULL,
  root_material_code VARCHAR(64) NOT NULL,
  source_month DATE NOT NULL,
  parent_material_code VARCHAR(64) DEFAULT NULL,
  current_material_code VARCHAR(64) NOT NULL,
  level_no INT NOT NULL COMMENT '0=root, 1=first child, etc.',
  path_text VARCHAR(1024) DEFAULT NULL COMMENT 'Human-readable path: A -> B -> D',
  path_hash VARCHAR(64) NOT NULL COMMENT 'Stable hash of path',
  category_role VARCHAR(32) DEFAULT NULL,
  supply_mode VARCHAR(32) DEFAULT NULL,
  explosion_policy VARCHAR(32) DEFAULT NULL,
  gross_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  inventory_before_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  inventory_used_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  inventory_after_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  net_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  terminal_flag TINYINT(1) NOT NULL DEFAULT 0,
  terminal_reason VARCHAR(64) DEFAULT NULL COMMENT 'STOP/STRUCTURAL_LEAF/EXPLODE_WITHOUT_BOM',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mrd_run_factory_root_month_path_material_level (run_id, factory_code, root_material_code, source_month, path_hash, current_material_code, level_no),
  KEY idx_mrd_run_root_material (run_id, root_material_code, current_material_code),
  KEY idx_mrd_batch_root (batch_id, root_material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_terminal_demand (
  id BIGINT NOT NULL AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  factory_code VARCHAR(32) NOT NULL,
  root_material_code VARCHAR(64) NOT NULL,
  terminal_material_code VARCHAR(64) NOT NULL,
  terminal_type VARCHAR(32) NOT NULL COMMENT 'FINISHED_GOOD/HEAD_ASSEMBLY/PCBA/RAW_MATERIAL/OTHER',
  supply_mode VARCHAR(32) NOT NULL DEFAULT 'UNMAINTAINED',
  source_month DATE NOT NULL,
  quantity DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mtd_run_factory_root_terminal_type_supply_month (run_id, factory_code, root_material_code, terminal_material_code, terminal_type, supply_mode, source_month),
  KEY idx_mtd_run_root_terminal (run_id, root_material_code, terminal_material_code),
  KEY idx_mtd_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_daily_plan_detail (
  id BIGINT NOT NULL AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  factory_code VARCHAR(32) NOT NULL,
  root_material_code VARCHAR(64) NOT NULL,
  terminal_material_code VARCHAR(64) NOT NULL,
  terminal_type VARCHAR(32) NOT NULL,
  supply_mode VARCHAR(32) NOT NULL DEFAULT 'UNMAINTAINED',
  path_group_key VARCHAR(256) NOT NULL COMMENT 'Stable grouping key for multi-path aggregation',
  source_month DATE NOT NULL,
  source_date DATE NOT NULL COMMENT 'Business demand date from calendar allocation',
  schedule_date DATE NOT NULL COMMENT 'source_date - 7 calendar days',
  system_quantity DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  is_production_day TINYINT(1) NOT NULL DEFAULT 1,
  revision INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mdpd_run_factory_root_terminal_type_supply_path_date (run_id, factory_code, root_material_code, terminal_material_code, terminal_type, supply_mode, path_group_key, schedule_date),
  KEY idx_mdpd_batch_factory_date (batch_id, factory_code, schedule_date),
  KEY idx_mdpd_batch_root_terminal_date (batch_id, root_material_code, terminal_material_code, schedule_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_daily_plan_override (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  factory_code VARCHAR(32) NOT NULL,
  root_material_code VARCHAR(64) NOT NULL,
  terminal_material_code VARCHAR(64) NOT NULL,
  terminal_type VARCHAR(32) NOT NULL,
  supply_mode VARCHAR(32) NOT NULL DEFAULT 'UNMAINTAINED',
  path_group_key VARCHAR(256) NOT NULL,
  schedule_date DATE NOT NULL,
  manual_quantity DECIMAL(18,6) NOT NULL,
  reason VARCHAR(512) DEFAULT NULL,
  revision INT NOT NULL DEFAULT 0,
  operator_id BIGINT DEFAULT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mdpo_batch_factory_root_terminal_type_supply_path_date (batch_id, factory_code, root_material_code, terminal_material_code, terminal_type, supply_mode, path_group_key, schedule_date),
  KEY idx_mdpo_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mrp_unallocated_quantity (
  id BIGINT NOT NULL AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  demand_key VARCHAR(256) NOT NULL,
  source_date DATE NOT NULL,
  attempted_schedule_date DATE NOT NULL,
  quantity DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
  reason_code VARCHAR(64) NOT NULL COMMENT 'TARGET_NON_PRODUCTION_DAY/NO_PRODUCTION_DAY',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED',
  resolved_schedule_date DATE DEFAULT NULL,
  revision INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_muu_run_demand_source_attempted (run_id, demand_key, source_date, attempted_schedule_date),
  KEY idx_muu_batch_status (batch_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
