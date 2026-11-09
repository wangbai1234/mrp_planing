-- 五项配置的当前主数据。旧 *_version / *_detail 保留，供已锁定批次只读回放。
-- 当前表由「每个配置 id 最大的已发布版本」初始化；业务周按月各取最新已发布。

CREATE TABLE factory_warehouse_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT,
    factory_code VARCHAR(32) NOT NULL,
    factory_name VARCHAR(128) NULL,
    warehouse_code VARCHAR(32) NOT NULL,
    warehouse_name VARCHAR(128) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_fwm_factory (factory_code),
    UNIQUE KEY uk_fwm_warehouse (warehouse_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE stock_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    warehouse_code VARCHAR(32) NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    safety_stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
    warning_stock_qty DECIMAL(18,6) NOT NULL DEFAULT 0.000000,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sp_warehouse_material (warehouse_code, material_code),
    CONSTRAINT chk_sp_non_negative CHECK (safety_stock_qty >= 0 AND warning_stock_qty >= 0),
    CONSTRAINT chk_sp_safety_warning CHECK (safety_stock_qty <= warning_stock_qty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE material_policy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_code VARCHAR(64) NOT NULL,
    supply_mode VARCHAR(32) NOT NULL DEFAULT 'UNMAINTAINED',
    explosion_policy VARCHAR(32) NOT NULL DEFAULT 'UNMAINTAINED',
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mp_material (material_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE mrp_production_calendar (
    id BIGINT NOT NULL AUTO_INCREMENT,
    factory_code VARCHAR(32) NOT NULL,
    calendar_date DATE NOT NULL,
    is_production_day TINYINT(1) NOT NULL DEFAULT 1,
    allocation_weight DECIMAL(18,6) NOT NULL DEFAULT 1.000000,
    daily_capacity DECIMAL(18,6) NULL,
    reason VARCHAR(256) NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mpc_factory_date (factory_code, calendar_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE business_week (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_month DATE NOT NULL,
    week_code VARCHAR(16) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    sort_no INT NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bw_month_week (business_month, week_code),
    UNIQUE KEY uk_bw_month_sort (business_month, sort_no),
    CONSTRAINT chk_bw_date_range CHECK (start_date <= end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO factory_warehouse_mapping
    (factory_code, factory_name, warehouse_code, warehouse_name, enabled, created_by, created_at, updated_by, updated_at)
SELECT d.factory_code, d.factory_name, d.warehouse_code, d.warehouse_name, d.enabled,
       v.created_by, COALESCE(v.published_at, v.created_at), v.created_by, COALESCE(v.published_at, v.created_at)
FROM factory_warehouse_mapping_detail d
JOIN factory_warehouse_mapping_version v ON v.id = d.version_id
WHERE v.status = 'PUBLISHED' AND v.is_deleted = 0
  AND v.id = (SELECT MAX(id) FROM factory_warehouse_mapping_version WHERE status = 'PUBLISHED' AND is_deleted = 0);

INSERT INTO stock_policy
    (warehouse_code, material_code, safety_stock_qty, warning_stock_qty, created_by, created_at, updated_by, updated_at)
SELECT d.warehouse_code, d.material_code, d.safety_stock_qty, d.warning_stock_qty,
       v.created_by, COALESCE(v.published_at, v.created_at), v.created_by, COALESCE(v.published_at, v.created_at)
FROM stock_policy_detail d
JOIN stock_policy_version v ON v.id = d.version_id
WHERE v.status = 'PUBLISHED' AND v.is_deleted = 0
  AND v.id = (SELECT MAX(id) FROM stock_policy_version WHERE status = 'PUBLISHED' AND is_deleted = 0);

INSERT INTO material_policy
    (material_code, supply_mode, explosion_policy, created_by, created_at, updated_by, updated_at)
SELECT d.material_code, d.supply_mode, d.explosion_policy,
       v.created_by, COALESCE(v.published_at, v.created_at), v.created_by, COALESCE(v.published_at, v.created_at)
FROM mrp_material_policy_detail d
JOIN mrp_material_policy_version v ON v.id = d.version_id
WHERE v.status = 'PUBLISHED' AND v.is_deleted = 0
  AND v.id = (SELECT MAX(id) FROM mrp_material_policy_version WHERE status = 'PUBLISHED' AND is_deleted = 0);

INSERT INTO mrp_production_calendar
    (factory_code, calendar_date, is_production_day, allocation_weight, daily_capacity, reason,
     created_by, created_at, updated_by, updated_at)
SELECT d.factory_code, d.calendar_date, d.is_production_day, d.allocation_weight, d.daily_capacity, d.reason,
       v.created_by, COALESCE(v.published_at, v.created_at), v.created_by, COALESCE(v.published_at, v.created_at)
FROM production_calendar_day d
JOIN production_calendar_version v ON v.id = d.version_id
WHERE v.status = 'PUBLISHED' AND v.is_deleted = 0
  AND v.id = (
      SELECT MAX(v2.id) FROM production_calendar_version v2
      WHERE v2.status = 'PUBLISHED' AND v2.is_deleted = 0 AND v2.factory_code = v.factory_code
  );

INSERT INTO business_week
    (business_month, week_code, start_date, end_date, sort_no, created_by, created_at, updated_by, updated_at)
SELECT v.business_month, d.week_code, d.start_date, d.end_date, d.sort_no,
       v.created_by, COALESCE(v.published_at, v.created_at), v.created_by, COALESCE(v.published_at, v.created_at)
FROM business_week_detail d
JOIN business_week_version v ON v.id = d.version_id
WHERE v.status = 'PUBLISHED' AND v.is_deleted = 0
  AND v.id = (
      SELECT MAX(v2.id) FROM business_week_version v2
      WHERE v2.status = 'PUBLISHED' AND v2.is_deleted = 0 AND v2.business_month = v.business_month
  );
