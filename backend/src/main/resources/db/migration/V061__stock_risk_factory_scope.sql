-- Risk rows need an explicit factory for materials shared by multiple factories.
ALTER TABLE mrp_stock_risk
    ADD COLUMN factory_code VARCHAR(64) NULL AFTER run_id;

CREATE INDEX idx_stock_risk_run_factory_material
    ON mrp_stock_risk(run_id, factory_code, material_code);
