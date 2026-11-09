ALTER TABLE mrp_stock_risk
    ADD COLUMN resolved_at DATETIME(3) NULL,
    ADD COLUMN resolved_by BIGINT NULL,
    ADD COLUMN resolution_code VARCHAR(48) NULL;
