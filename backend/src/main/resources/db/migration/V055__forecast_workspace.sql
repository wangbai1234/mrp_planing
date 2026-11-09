-- Forecast edits are prepared separately. Confirmed forecast versions remain immutable.
ALTER TABLE forecast_version
    ADD COLUMN maker VARCHAR(128) NULL,
    ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'IMPORT',
    ADD COLUMN parent_version_id BIGINT NULL,
    ADD COLUMN published_by BIGINT NULL,
    ADD COLUMN published_at DATETIME(3) NULL;
UPDATE forecast_version SET published_by=created_by, published_at=created_at WHERE published_at IS NULL;
CREATE TABLE forecast_version_head (
    id TINYINT NOT NULL PRIMARY KEY,
    current_version_id BIGINT NULL,
    revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO forecast_version_head(id, current_version_id) SELECT 1, MAX(id) FROM forecast_version;
CREATE TABLE forecast_draft (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    base_version_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    maker VARCHAR(128) NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    published_version_id BIGINT NULL,
    active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status='DRAFT' THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY uk_forecast_single_draft (active_slot),
    KEY idx_forecast_draft_base (base_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE forecast_draft_line (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    factory_code VARCHAR(32) NOT NULL,
    material_id VARCHAR(64) NOT NULL,
    material_name VARCHAR(255) NULL,
    business_line VARCHAR(64) NULL,
    form_type VARCHAR(64) NULL,
    project VARCHAR(128) NULL,
    platform VARCHAR(256) NULL,
    mold VARCHAR(128) NULL,
    status VARCHAR(64) NULL,
    months_json JSON NOT NULL,
    priority_seq INT NOT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_forecast_draft_material (draft_id, factory_code, material_id),
    KEY idx_forecast_draft_order (draft_id, priority_seq, id),
    CONSTRAINT fk_forecast_draft_line FOREIGN KEY (draft_id) REFERENCES forecast_draft(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @forecast_page = (SELECT id FROM permission WHERE code='forecast' LIMIT 1);
INSERT IGNORE INTO permission(code,name,type,parent_id,resource,action,sort) VALUES
('forecast:create','新增经营计划','BUTTON',@forecast_page,'forecast','create',7),
('forecast:edit','编辑经营计划','BUTTON',@forecast_page,'forecast','edit',8),
('forecast:delete','删除经营计划','BUTTON',@forecast_page,'forecast','delete',9),
('forecast:publish','发布经营计划','BUTTON',@forecast_page,'forecast','publish',10),
('forecast:log','查看经营计划日志','BUTTON',@forecast_page,'forecast','log',11);
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code IN
('forecast:create','forecast:edit','forecast:delete','forecast:publish','forecast:log') WHERE r.code='ADMIN';
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code IN
('forecast:create','forecast:edit','forecast:delete','forecast:publish','forecast:log') WHERE r.code='BUSINESS';
