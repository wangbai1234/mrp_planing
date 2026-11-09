-- Confirmation is review metadata for one calculation run, not a material policy value.
ALTER TABLE mrp_material_policy_notice
    ADD COLUMN default_explosion_confirmed_by BIGINT NULL,
    ADD COLUMN default_explosion_confirmed_at DATETIME(3) NULL;
