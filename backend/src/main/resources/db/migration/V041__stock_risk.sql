CREATE TABLE mrp_stock_risk (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    risk_type VARCHAR(32) NOT NULL,
    warehouse_code VARCHAR(32) NULL,
    material_code VARCHAR(64) NOT NULL,
    available_qty DECIMAL(18,6) NULL,
    used_qty DECIMAL(18,6) NULL,
    remaining_qty DECIMAL(18,6) NULL,
    safety_stock_qty DECIMAL(18,6) NULL,
    warning_stock_qty DECIMAL(18,6) NULL,
    root_material_codes VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_risk (batch_id, run_id, risk_type, warehouse_code, material_code),
    KEY idx_stock_risk_batch (batch_id, run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
