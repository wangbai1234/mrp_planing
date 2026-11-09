-- The same machine/month can be planned independently by both factories.
-- Existing rows and nullable historical factory codes are preserved.
ALTER TABLE forecast_detail
    DROP INDEX uk_fd_version_material_month,
    ADD UNIQUE KEY uk_fd_version_factory_material_month (version_id, factory_code, material_id, plan_month);
