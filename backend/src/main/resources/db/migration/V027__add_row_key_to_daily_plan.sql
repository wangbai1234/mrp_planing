ALTER TABLE mrp_daily_plan_detail ADD COLUMN row_key VARCHAR(64) AFTER revision;
CREATE INDEX idx_daily_plan_row_key ON mrp_daily_plan_detail(row_key);
