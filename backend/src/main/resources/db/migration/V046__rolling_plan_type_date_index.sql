-- Supports factory-wide machine load and date-filtered cell detail reads.
CREATE INDEX idx_mdpd_run_type_date_factory
    ON mrp_daily_plan_detail (run_id, terminal_type, schedule_date, factory_code);
