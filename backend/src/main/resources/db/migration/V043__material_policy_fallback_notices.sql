-- Keep historical snapshots and legacy rows intact; new configuration requires explicit values.
ALTER TABLE material_policy ALTER COLUMN supply_mode DROP DEFAULT, ALTER COLUMN explosion_policy DROP DEFAULT;
ALTER TABLE mrp_requirement_detail
    ADD COLUMN effective_explosion_policy VARCHAR(32) NULL,
    ADD COLUMN policy_source VARCHAR(32) NULL;
CREATE TABLE mrp_material_policy_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    factory_code VARCHAR(32) NOT NULL,
    material_code VARCHAR(64) NOT NULL,
    material_name VARCHAR(255) NULL,
    missing_type VARCHAR(32) NOT NULL,
    effective_explosion_policy VARCHAR(32) NOT NULL,
    sources_json LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_policy_notice_run_material (run_id, factory_code, material_code),
    KEY idx_policy_notice_batch_run (batch_id, run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
