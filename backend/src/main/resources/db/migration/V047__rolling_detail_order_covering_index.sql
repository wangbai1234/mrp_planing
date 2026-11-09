-- Preserve the complete stable detail order and defer wide row reads until after pagination.
CREATE INDEX idx_mdpd_run_detail_order
    ON mrp_daily_plan_detail (run_id, schedule_date, source_month, factory_code,
        root_material_code, terminal_material_code, path_group_key, terminal_type,
        supply_mode, system_quantity);
